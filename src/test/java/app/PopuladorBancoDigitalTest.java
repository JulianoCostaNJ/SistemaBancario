package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Conta;
import domain.Transacao;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Month;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.TransacaoRepositorySqlite;

/**
 * Testes de {@link PopuladorBancoDigital}: o histórico financeiro gravado fecha com o protótipo.
 */
class PopuladorBancoDigitalTest {

    @TempDir
    Path pasta;

    private static PrintStream saida(ByteArrayOutputStream buffer) {
        return new PrintStream(buffer, true, StandardCharsets.UTF_8);
    }

    private static Conta titular(List<Conta> contas, String nome) {
        return contas.stream().filter(c -> c.getTitular().getNome().equals(nome)).findFirst().orElseThrow();
    }

    private static void assertValor(String esperado, BigDecimal obtido) {
        assertEquals(0, new BigDecimal(esperado).compareTo(obtido),
                "Esperado " + esperado + " mas foi " + obtido);
    }

    @Test
    void gravaContasSaldosEMovimentosDoPrototipo() {
        PopuladorBancoDigital.executar(pasta, saida(new ByteArrayOutputStream()));

        FabricaConexao fabrica = FabricaConexao.paraArquivo(pasta.resolve("poo-bank-digital.db"));
        List<Conta> contas = new ContaRepositorySqlite(fabrica).listarTodas();
        TransacaoRepositorySqlite transacoes = new TransacaoRepositorySqlite(fabrica);

        assertEquals(2, contas.size());
        Conta mariana = titular(contas, "Mariana Costa");
        Conta lucas = titular(contas, "Lucas Almeida");
        assertValor("2950.00", mariana.consultarSaldo());
        assertValor("1850.00", lucas.consultarSaldo());

        List<Transacao> extrato = transacoes.consultarExtrato(mariana.getNumero());
        assertEquals(6, extrato.size());
        BigDecimal entradas = BigDecimal.ZERO;
        BigDecimal saidas = BigDecimal.ZERO;
        for (Transacao transacao : extrato) {
            if (transacao.getHorario().getMonth() != Month.NOVEMBER) {
                continue;
            }
            if (Long.valueOf(mariana.getNumero()).equals(transacao.getContaDestino())) {
                entradas = entradas.add(transacao.getValor());
            }
            if (Long.valueOf(mariana.getNumero()).equals(transacao.getContaOrigem())) {
                saidas = saidas.add(transacao.getValor());
            }
        }
        assertValor("1500.00", entradas);
        assertValor("550.00", saidas);
        assertEquals(1, transacoes.consultarExtrato(lucas.getNumero()).size());
    }

    @Test
    void segundaExecucaoNaoDuplicaNada() {
        PopuladorBancoDigital.executar(pasta, saida(new ByteArrayOutputStream()));
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        PopuladorBancoDigital.executar(pasta, saida(buffer));

        assertTrue(buffer.toString(StandardCharsets.UTF_8).contains("já foi populado"));
        FabricaConexao fabrica = FabricaConexao.paraArquivo(pasta.resolve("poo-bank-digital.db"));
        List<Conta> contas = new ContaRepositorySqlite(fabrica).listarTodas();
        assertEquals(2, contas.size());
        assertEquals(6, new TransacaoRepositorySqlite(fabrica)
                .consultarExtrato(titular(contas, "Mariana Costa").getNumero()).size());
    }

    @Test
    void recusaArgumentosNulos() {
        PrintStream saida = saida(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class, () -> PopuladorBancoDigital.executar(null, saida));
        assertThrows(IllegalArgumentException.class, () -> PopuladorBancoDigital.executar(pasta, null));
    }
}
