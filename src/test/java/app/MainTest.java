package app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import domain.Conta;
import domain.Transacao;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.TransacaoRepositorySqlite;

/**
 * Testes de integração da classe {@link Main}.
 */
class MainTest {

    private static final Clock RELOGIO_FIXO =
            Clock.fixed(Instant.parse("2026-11-05T14:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path pastaTemp;

    @Test
    void primeiraExecucaoCriaContasRodaSimulacoesEGeraExtrato() throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream saida = new PrintStream(buffer, true, StandardCharsets.UTF_8);

        Main.executar(pastaTemp, saida, RELOGIO_FIXO);

        String log = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("Contas recuperadas do banco: 0"),
                "Na primeira execução não deve haver contas prévias.");
        assertTrue(log.contains("Resultado Simulação A: ESPERADO IGUAL AO OBTIDO"),
                "A simulação em memória deve terminar com saldos exatos.");
        assertTrue(log.contains("Resultado Simulação B: ESPERADO IGUAL AO OBTIDO"),
                "A simulação persistida deve terminar com saldos exatos.");

        Path arquivoBanco = pastaTemp.resolve("poo-bank.db");
        Path arquivoExtrato = pastaTemp.resolve("extrato-conta-000001.txt");
        assertTrue(Files.exists(arquivoBanco), "O arquivo do banco SQLite deve ser criado.");
        assertTrue(Files.exists(arquivoExtrato), "O arquivo de extrato deve ser criado.");

        FabricaConexao fabrica = FabricaConexao.paraArquivo(arquivoBanco);
        ContaRepositorySqlite repositorioContas = new ContaRepositorySqlite(fabrica);
        TransacaoRepositorySqlite repositorioTransacoes = new TransacaoRepositorySqlite(fabrica);

        List<Conta> contas = repositorioContas.listarTodas();
        assertEquals(3, contas.size(), "Devem existir exatamente 3 contas gravadas.");
        assertEquals(new BigDecimal("1310.50"), contas.get(0).consultarSaldo());
        assertEquals(new BigDecimal("989.00"), contas.get(1).consultarSaldo());
        assertEquals(new BigDecimal("1050.00"), contas.get(2).consultarSaldo());

        List<Transacao> extratoConta1 = repositorioTransacoes.consultarExtrato(1L);
        assertEquals(10, extratoConta1.size(),
                "A conta 1 deve possuir 10 movimentos concluídos na primeira execução.");

        String conteudoExtrato = Files.readString(arquivoExtrato, StandardCharsets.UTF_8);
        assertTrue(conteudoExtrato.contains("POO BANK - EXTRATO DE CONTA"));
        assertTrue(conteudoExtrato.contains("Agência ..........: 0001"));
        assertTrue(conteudoExtrato.contains("Conta ............: 000001"));
        assertTrue(conteudoExtrato.contains("Emitido em .......: 2026-11-05 14:30:00"));
        assertTrue(conteudoExtrato.contains("DEPÓSITO"));
        assertTrue(conteudoExtrato.contains("SAQUE"));
        assertTrue(conteudoExtrato.contains("TRANSFERÊNCIA"));
        assertTrue(conteudoExtrato.contains("Movimentos: 10"));
        assertTrue(conteudoExtrato.contains("Saldo atual: R$ 1.310,50"));
    }
    @Test
    void primeiraExecucaoCriaTambemAsTabelasDoBancoDigital() throws Exception {
        Main.executar(pastaTemp, new PrintStream(new ByteArrayOutputStream(), true,
                StandardCharsets.UTF_8), RELOGIO_FIXO);

        FabricaConexao fabrica = FabricaConexao.paraArquivo(pastaTemp.resolve("poo-bank.db"));
        try (Connection conexao = fabrica.criarConexao();
            Statement comando = conexao.createStatement();
            ResultSet resultado = comando.executeQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                            + "AND name IN ('cartoes', 'faturas', 'chaves_pix', 'metas')")) {
            resultado.next();
            assertEquals(4, resultado.getInt(1));
        }
    }
    @Test
    void segundaExecucaoRecuperaContasSemDuplicarEAcumulaSaldos() throws IOException {
        ByteArrayOutputStream buffer1 = new ByteArrayOutputStream();
        Main.executar(pastaTemp, new PrintStream(buffer1, true, StandardCharsets.UTF_8), RELOGIO_FIXO);

        ByteArrayOutputStream buffer2 = new ByteArrayOutputStream();
        Main.executar(pastaTemp, new PrintStream(buffer2, true, StandardCharsets.UTF_8), RELOGIO_FIXO);

        String logSegunda = buffer2.toString(StandardCharsets.UTF_8);
        assertTrue(logSegunda.contains("Contas recuperadas do banco: 3"),
                "A segunda execução deve recuperar as 3 contas gravadas.");
        assertFalse(logSegunda.contains("Contas recuperadas do banco: 0"));
        assertTrue(logSegunda.contains("Resultado Simulação A: ESPERADO IGUAL AO OBTIDO"));
        assertTrue(logSegunda.contains("Resultado Simulação B: ESPERADO IGUAL AO OBTIDO"));

        FabricaConexao fabrica = FabricaConexao.paraArquivo(pastaTemp.resolve("poo-bank.db"));
        ContaRepositorySqlite repositorioContas = new ContaRepositorySqlite(fabrica);
        TransacaoRepositorySqlite repositorioTransacoes = new TransacaoRepositorySqlite(fabrica);

        List<Conta> contas = repositorioContas.listarTodas();
        assertEquals(3, contas.size(), "Não deve duplicar contas na segunda execução.");
        assertEquals(new BigDecimal("1621.00"), contas.get(0).consultarSaldo());
        assertEquals(new BigDecimal("978.00"), contas.get(1).consultarSaldo());
        assertEquals(new BigDecimal("1100.00"), contas.get(2).consultarSaldo());

        List<Transacao> extratoConta1 = repositorioTransacoes.consultarExtrato(1L);
        assertEquals(20, extratoConta1.size(),
                "O extrato da conta 1 deve acumular 20 movimentos após duas execuções.");

        String conteudoExtrato = Files.readString(
                pastaTemp.resolve("extrato-conta-000001.txt"), StandardCharsets.UTF_8);
        assertTrue(conteudoExtrato.contains("Movimentos: 20"));
        assertTrue(conteudoExtrato.contains("Saldo atual: R$ 1.621,00"));
    }

    @Test
    void recusaArgumentosNulos() {
        PrintStream saida = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);

        assertThrows(IllegalArgumentException.class,
                () -> Main.executar(null, saida, RELOGIO_FIXO));
        assertThrows(IllegalArgumentException.class,
                () -> Main.executar(pastaTemp, null, RELOGIO_FIXO));
        assertThrows(IllegalArgumentException.class,
                () -> Main.executar(pastaTemp, saida, null));
    }
}