package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.TipoTransacao;
import domain.Transacao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.TransacaoRepositorySqlite;

/**
 * Testes de {@link ExportadorExtrato}. Cada teste lê o arquivo gravado e verifica o conteúdo.
 */
class ExportadorExtratoTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-10-23T13:30:15Z"), ZoneOffset.UTC);
    private static final long CONTA = 12L;

    @TempDir
    Path pasta;

    private ExportadorExtrato exportador;
    private Path arquivo;

    @BeforeEach
    void preparar() {
        exportador = new ExportadorExtrato(RELOGIO);
        arquivo = pasta.resolve("extrato.txt");
    }

    private static Transacao deposito(long id, String valor, LocalDateTime horario) {
        return new Transacao(id, null, CONTA, TipoTransacao.DEPOSITO, new BigDecimal(valor), horario);
    }

    private static Transacao saque(long id, String valor, LocalDateTime horario) {
        return new Transacao(id, CONTA, null, TipoTransacao.SAQUE, new BigDecimal(valor), horario);
    }

    private static Transacao transferencia(long id, long origem, long destino, String valor,
                                           LocalDateTime horario) {
        return new Transacao(id, origem, destino, TipoTransacao.TRANSFERENCIA,
                new BigDecimal(valor), horario);
    }

    private static LocalDateTime em(int hora, int minuto, int segundo) {
        return LocalDateTime.of(2026, 10, 23, hora, minuto, segundo);
    }

    private List<Transacao> quatroMovimentos() {
        return List.of(
                deposito(1L, "500.00", em(10, 30, 0)),
                saque(2L, "120.50", em(10, 35, 10)),
                transferencia(3L, CONTA, 7L, "80.00", em(10, 40, 20)),
                transferencia(4L, 7L, CONTA, "30.00", em(10, 45, 30)));
    }

    private List<String> exportarELer(String saldo, List<Transacao> transacoes) throws IOException {
        exportador.exportar(CONTA, new BigDecimal(saldo), transacoes, arquivo);
        return Files.readAllLines(arquivo, StandardCharsets.UTF_8);
    }

    private static String linhaCom(List<String> linhas, String trecho) {
        return linhas.stream().filter(linha -> linha.contains(trecho)).findFirst()
                .orElseThrow(() -> new AssertionError("Nenhuma linha contém: " + trecho));
    }

    // ---------- cabeçalho e rodapé ----------

    @Test
    void cabecalhoTemAgenciaContaComSeisDigitosEmitidoEmESaldoAtual() throws Exception {
        List<String> linhas = exportarELer("1299.50", quatroMovimentos());

        assertEquals("=".repeat(70), linhas.get(0));
        assertEquals(" POO BANK - EXTRATO DE CONTA", linhas.get(1));
        assertEquals(" Agência ..........: 0001", linhaCom(linhas, "Agência"));
        assertEquals(" Conta ............: 000012", linhaCom(linhas, "Conta ...."));
        assertEquals(" Emitido em .......: 2026-10-23 13:30:15", linhaCom(linhas, "Emitido em"));
        assertEquals(" Saldo atual: R$ 1.299,50", linhaCom(linhas, "Saldo atual"));
        assertEquals("=".repeat(70), linhas.get(linhas.size() - 1));
    }

    @Test
    void rodapeContaOsMovimentos() throws Exception {
        List<String> linhas = exportarELer("1299.50", quatroMovimentos());

        assertTrue(linhaCom(linhas, "Movimentos:").contains("Movimentos: 4"));
    }

    // ---------- movimentos ----------

    @Test
    void depositoApareceComoEntradaSemContaRelacionada() throws Exception {
        String linha = linhaCom(exportarELer("1.00", quatroMovimentos()), "DEPÓSITO");

        assertTrue(linha.contains("2026-10-23 10:30:00"), linha);
        assertTrue(linha.matches(".*DEPÓSITO\\s+-\\s+\\+500,00"), linha);
    }

    @Test
    void saqueApareceComoSaidaSemContaRelacionada() throws Exception {
        String linha = linhaCom(exportarELer("1.00", quatroMovimentos()), "SAQUE");

        assertTrue(linha.contains("2026-10-23 10:35:10"), linha);
        assertTrue(linha.matches(".*SAQUE\\s+-\\s+-120,50"), linha);
    }

    @Test
    void transferenciaEnviadaMostraODestinoComoSaida() throws Exception {
        String linha = linhaCom(exportarELer("1.00", quatroMovimentos()), "2026-10-23 10:40:20");

        assertTrue(linha.matches(".*TRANSFERÊNCIA\\s+000007\\s+-80,00"), linha);
    }

    @Test
    void transferenciaRecebidaMostraAOrigemComoEntrada() throws Exception {
        String linha = linhaCom(exportarELer("1.00", quatroMovimentos()), "2026-10-23 10:45:30");

        assertTrue(linha.matches(".*TRANSFERÊNCIA\\s+000007\\s+\\+30,00"), linha);
    }

    @Test
    void aOrdemRecebidaEhPreservada() throws Exception {
        List<Transacao> embaralhadas = List.of(
                saque(2L, "120.50", em(10, 35, 10)),
                deposito(1L, "500.00", em(10, 30, 0)));

        List<String> linhas = exportarELer("1.00", embaralhadas);

        assertTrue(linhas.indexOf(linhaCom(linhas, "SAQUE")) < linhas.indexOf(linhaCom(linhas, "DEPÓSITO")));
    }

    @Test
    void extratoSemMovimentosMostraAviso() throws Exception {
        List<String> linhas = exportarELer("10.00", List.of());

        assertTrue(linhas.contains(" Nenhum movimento."));
        assertTrue(linhaCom(linhas, "Movimentos:").contains("Movimentos: 0"));
    }

    // ---------- formato monetário ----------

    @Test
    void valoresUsamMilharEVirgulaSemPerderPrecisao() throws Exception {
        List<Transacao> movimentos = List.of(
                deposito(1L, "12345678901234567.89", em(10, 0, 0)),
                deposito(2L, "0.05", em(10, 1, 0)));

        List<String> linhas = exportarELer("12345678901234567.94", movimentos);

        assertTrue(linhaCom(linhas, "10:00:00").endsWith("+12.345.678.901.234.567,89"));
        assertTrue(linhaCom(linhas, "10:01:00").endsWith("+0,05"));
        assertEquals(" Saldo atual: R$ 12.345.678.901.234.567,94", linhaCom(linhas, "Saldo atual"));
    }

    // ---------- arquivo ----------

    @Test
    void arquivoEhUtf8SemBom() throws Exception {
        exportador.exportar(CONTA, new BigDecimal("1.00"), quatroMovimentos(), arquivo);

        byte[] bytes = Files.readAllBytes(arquivo);

        boolean comBom = bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB
                && bytes[2] == (byte) 0xBF;
        assertFalse(comBom, "O arquivo não deve começar com BOM.");
        assertTrue(new String(bytes, StandardCharsets.UTF_8).contains("DEPÓSITO"));
        assertFalse(new String(bytes, StandardCharsets.ISO_8859_1).contains("DEPÓSITO"),
                "Os acentos devem estar gravados em UTF-8, e não em ISO-8859-1.");
    }

    @Test
    void reexportarSobrescreveOArquivoAnterior() throws Exception {
        exportador.exportar(CONTA, new BigDecimal("1.00"), quatroMovimentos(), arquivo);

        List<String> linhas = exportarELer("1.00", List.of());

        assertFalse(linhas.stream().anyMatch(linha -> linha.contains("DEPÓSITO")));
    }

    @Test
    void pastaInexistenteGeraUncheckedIOException() {
        Path semPasta = pasta.resolve("nao-existe").resolve("extrato.txt");

        assertThrows(UncheckedIOException.class,
                () -> exportador.exportar(CONTA, new BigDecimal("1.00"), List.of(), semPasta));
    }

    @Test
    void destinoQueEUmDiretorioGeraUncheckedIOException() {
        assertThrows(UncheckedIOException.class,
                () -> exportador.exportar(CONTA, new BigDecimal("1.00"), List.of(), pasta));
    }

    // ---------- validações ----------

    @Test
    void argumentosNulosOuInvalidosSaoRejeitadosSemCriarArquivo() {
        BigDecimal saldo = new BigDecimal("1.00");
        List<Transacao> comNulo = new ArrayList<>(Arrays.asList(deposito(1L, "1.00", em(10, 0, 0)), null));

        assertThrows(IllegalArgumentException.class, () -> new ExportadorExtrato(null));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(CONTA, null, List.of(), arquivo));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(CONTA, saldo, null, arquivo));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(CONTA, saldo, List.of(), null));
        assertThrows(IllegalArgumentException.class, () -> exportador.exportar(CONTA, saldo, comNulo, arquivo));
        assertThrows(IllegalArgumentException.class,
                () -> exportador.exportar(CONTA, new BigDecimal("10.001"), List.of(), arquivo));
        assertFalse(Files.exists(arquivo));
    }

    @Test
    void transacaoDeOutraContaEhRejeitadaSemCriarArquivo() {
        List<Transacao> deOutraConta = List.of(transferencia(9L, 7L, 8L, "5.00", em(10, 0, 0)));

        assertThrows(IllegalArgumentException.class,
                () -> exportador.exportar(CONTA, new BigDecimal("1.00"), deOutraConta, arquivo));
        assertFalse(Files.exists(arquivo));
    }

    // ---------- ponta a ponta: serviço + SQLite + arquivo ----------

    @Test
    void extratoExportadoAPartirDoServicoComSqliteTemOsMovimentosCertos() throws Exception {
        FabricaConexao fabrica = FabricaConexao.paraArquivo(pasta.resolve("banco.db"));
        fabrica.inicializarSchema();
        ServicoBancario servico = new ServicoBancario(new ContaRepositorySqlite(fabrica),
                new TransacaoRepositorySqlite(fabrica), RELOGIO);
        long ana = servico.abrirConta("Ana Souza", "111", new BigDecimal("1000.00"));
        long bruno = servico.abrirConta("Bruno Lima", "222", new BigDecimal("0.00"));
        long carla = servico.abrirConta("Carla Dias", "333", new BigDecimal("0.00"));
        servico.depositar(ana, new BigDecimal("500.00"));
        servico.sacar(ana, new BigDecimal("120.50"));
        servico.transferir(ana, bruno, new BigDecimal("80.00"));
        servico.transferir(bruno, carla, new BigDecimal("10.00"));

        exportador.exportar(ana, servico.consultarSaldo(ana), servico.consultarExtrato(ana), arquivo);
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);

        assertEquals(" Conta ............: 000001", linhaCom(linhas, "Conta ...."));
        assertEquals(" Saldo atual: R$ 1.299,50", linhaCom(linhas, "Saldo atual"));
        assertTrue(linhaCom(linhas, "Movimentos:").contains("Movimentos: 3"));
        assertTrue(linhaCom(linhas, "DEPÓSITO").endsWith("+500,00"));
        assertTrue(linhaCom(linhas, "SAQUE").endsWith("-120,50"));
        assertTrue(linhaCom(linhas, "TRANSFERÊNCIA").matches(".*TRANSFERÊNCIA\\s+000002\\s+-80,00"));
        assertEquals(1, linhas.stream().filter(linha -> linha.contains("TRANSFERÊNCIA")).count(),
                "A transferência entre Bruno e Carla não pertence ao extrato da Ana.");
    }
}
