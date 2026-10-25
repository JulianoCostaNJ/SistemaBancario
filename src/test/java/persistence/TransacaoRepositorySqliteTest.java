package persistence;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import domain.Transacao;

/**
 * Testes de {@link TransacaoRepositorySqlite} contra um banco SQLite em arquivo temporário e
 * isolado.
 */
class TransacaoRepositorySqliteTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 23, 10, 30, 15, 123_456_000);

    @TempDir
    Path pasta;

    private FabricaConexao fabrica;
    private ContaRepositorySqlite contas;
    private TransacaoRepositorySqlite repositorio;

    private final Cliente ana = new Cliente(1L, "Ana Souza", "123.456.789-09");
    private final Cliente bruno = new Cliente(2L, "Bruno Lima", "987.654.321-00");

    @BeforeEach
    void prepararBanco() {
        fabrica = FabricaConexao.paraArquivo(pasta.resolve("teste.db"));
        fabrica.inicializarSchema();
        contas = new ContaRepositorySqlite(fabrica);
        repositorio = new TransacaoRepositorySqlite(fabrica);
    }

    private Conta contaSalva(long numero, Cliente titular, String saldo) {
        Conta conta = new Conta(numero, titular, new BigDecimal(saldo));
        contas.inserir(conta);
        return conta;
    }

    private static Transacao deposito(long id, long destino, String valor, LocalDateTime horario) {
        return new Transacao(id, null, destino, TipoTransacao.DEPOSITO, new BigDecimal(valor), horario);
    }

    private static Transacao transferencia(long id, long origem, long destino, String valor,
                                           LocalDateTime horario) {
        return new Transacao(id, origem, destino, TipoTransacao.TRANSFERENCIA,
                new BigDecimal(valor), horario);
    }

    private void executarSql(String sql) throws SQLException {
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement()) {
            comando.execute(sql);
        }
    }

    private int contar(String tabela) throws SQLException {
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery("SELECT COUNT(*) FROM " + tabela)) {
            resultado.next();
            return resultado.getInt(1);
        }
    }

    private void assertSaldoNoBanco(String esperado, long numeroConta) {
        BigDecimal saldo = contas.buscarPorNumero(numeroConta).orElseThrow().consultarSaldo();
        assertEquals(0, new BigDecimal(esperado).compareTo(saldo),
                "Saldo esperado " + esperado + " mas foi " + saldo);
    }

    private static List<Long> ids(List<Transacao> transacoes) {
        return transacoes.stream().map(Transacao::getId).toList();
    }

    // ---------- identificadores ----------

    @Test
    void proximoIdComecaEmUmEmBancoVazio() {
        assertEquals(1L, repositorio.proximoId());
        assertEquals(2L, repositorio.proximoId());
    }

    @Test
    void proximoIdContinuaDepoisDoMaiorIdGravado() {
        Conta conta = contaSalva(10L, ana, "100.00");
        repositorio.registrarOperacao(deposito(5L, 10L, "10.00", HORARIO), List.of(conta));

        TransacaoRepositorySqlite novaInstancia = new TransacaoRepositorySqlite(fabrica);

        assertEquals(6L, novaInstancia.proximoId());
    }

    @Test
    void proximoIdNaoRepeteEntreThreads() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        });
        Set<Long> obtidos = ConcurrentHashMap.newKeySet();
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                futuros.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        obtidos.add(repositorio.proximoId());
                    }
                }));
            }
            for (Future<?> futuro : futuros) {
                futuro.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(800, obtidos.size(), "Houve identificadores repetidos.");
    }

    // ---------- registrar operação ----------

    @Test
    void depositoRegistradoAtualizaOSaldoEGuardaATransacao() {
        Conta conta = contaSalva(10L, ana, "100.00");
        conta.depositar(new BigDecimal("50.00"));
        long id = repositorio.proximoId();

        repositorio.registrarOperacao(deposito(id, 10L, "50.00", HORARIO), List.of(conta));

        assertSaldoNoBanco("150.00", 10L);
        List<Transacao> extrato = repositorio.consultarExtrato(10L);
        assertEquals(1, extrato.size());
        Transacao gravada = extrato.get(0);
        assertEquals(id, gravada.getId());
        assertEquals(TipoTransacao.DEPOSITO, gravada.getTipo());
        assertNull(gravada.getContaOrigem());
        assertEquals(10L, gravada.getContaDestino());
        assertEquals(0, new BigDecimal("50.00").compareTo(gravada.getValor()));
        assertEquals(HORARIO, gravada.getHorario());
    }

    @Test
    void transferenciaRegistradaAtualizaAsDuasContasEGuardaUmaUnicaTransacao() throws Exception {
        Conta origem = contaSalva(10L, ana, "100.00");
        Conta destino = contaSalva(20L, bruno, "50.00");
        origem.transferirPara(destino, new BigDecimal("30.00"));

        repositorio.registrarOperacao(transferencia(repositorio.proximoId(), 10L, 20L, "30.00", HORARIO),
                List.of(origem, destino));

        assertSaldoNoBanco("70.00", 10L);
        assertSaldoNoBanco("80.00", 20L);
        assertEquals(1, contar("transacoes"));
    }

    @Test
    void operacaoComContaNaoGravadaDesfazTudo() throws Exception {
        Conta gravada = contaSalva(10L, ana, "100.00");
        gravada.depositar(new BigDecimal("10.00"));
        Conta naoGravada = new Conta(99L, bruno, new BigDecimal("50.00"));

        assertThrows(PersistenciaException.class, () -> repositorio.registrarOperacao(
                deposito(1L, 10L, "10.00", HORARIO), List.of(gravada, naoGravada)));

        assertEquals(0, contar("transacoes"), "A transação não pode ficar gravada pela metade.");
        assertSaldoNoBanco("100.00", 10L);
    }

    @Test
    void idDeTransacaoRepetidoEhRejeitadoENaoAlteraOsSaldos() throws Exception {
        Conta conta = contaSalva(10L, ana, "100.00");
        conta.depositar(new BigDecimal("50.00"));
        repositorio.registrarOperacao(deposito(1L, 10L, "50.00", HORARIO), List.of(conta));
        conta.depositar(new BigDecimal("25.00"));

        assertThrows(PersistenciaException.class, () -> repositorio.registrarOperacao(
                deposito(1L, 10L, "25.00", HORARIO), List.of(conta)));

        assertSaldoNoBanco("150.00", 10L);
        assertEquals(1, contar("transacoes"));
    }

    @Test
    void registrarRejeitaArgumentosNulos() {
        Conta conta = contaSalva(10L, ana, "100.00");
        Transacao transacao = deposito(1L, 10L, "10.00", HORARIO);

        assertThrows(IllegalArgumentException.class,
                () -> repositorio.registrarOperacao(null, List.of(conta)));
        assertThrows(IllegalArgumentException.class,
                () -> repositorio.registrarOperacao(transacao, null));
        assertThrows(IllegalArgumentException.class,
                () -> repositorio.registrarOperacao(transacao, Arrays.asList(conta, null)));
    }

    @Test
    void valorEHorarioSaoGravadosComoTextoNoFormatoFixo() throws Exception {
        Conta conta = contaSalva(10L, ana, "100.00");
        repositorio.registrarOperacao(
                deposito(1L, 10L, "0.10", LocalDateTime.of(2026, 10, 23, 10, 30)), List.of(conta));

        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT typeof(valor), valor, horario FROM transacoes WHERE id = 1")) {
            assertTrue(resultado.next());
            assertEquals("text", resultado.getString(1));
            assertEquals("0.10", resultado.getString(2));
            assertEquals("2026-10-23 10:30:00.000000", resultado.getString(3));
        }
    }

    // ---------- extrato ----------

    @Test
    void extratoDeContaSemTransacoesEhVazio() {
        contaSalva(10L, ana, "100.00");

        assertTrue(repositorio.consultarExtrato(10L).isEmpty());
    }

    @Test
    void extratoNaoMisturaTransacoesDeOutrasContas() {
        Conta c10 = contaSalva(10L, ana, "100.00");
        Conta c20 = contaSalva(20L, bruno, "100.00");
        Conta c30 = contaSalva(30L, bruno, "100.00");
        repositorio.registrarOperacao(deposito(1L, 10L, "1.00", HORARIO), List.of(c10));
        repositorio.registrarOperacao(deposito(2L, 20L, "2.00", HORARIO.plusSeconds(1)), List.of(c20));
        repositorio.registrarOperacao(transferencia(3L, 10L, 30L, "3.00", HORARIO.plusSeconds(2)),
                List.of(c10, c30));

        assertEquals(List.of(1L, 3L), ids(repositorio.consultarExtrato(10L)));
        assertEquals(List.of(2L), ids(repositorio.consultarExtrato(20L)));
        assertEquals(List.of(3L), ids(repositorio.consultarExtrato(30L)));
    }

    @Test
    void extratoPermiteDistinguirEntradaESaidaDeUmaTransferencia() {
        Conta origem = contaSalva(10L, ana, "100.00");
        Conta destino = contaSalva(20L, bruno, "0.00");
        repositorio.registrarOperacao(transferencia(1L, 10L, 20L, "5.00", HORARIO),
                List.of(origem, destino));

        Transacao naOrigem = repositorio.consultarExtrato(10L).get(0);
        Transacao noDestino = repositorio.consultarExtrato(20L).get(0);

        assertEquals(10L, naOrigem.getContaOrigem(), "Para a conta 10 a transferência é uma saída.");
        assertEquals(20L, noDestino.getContaDestino(), "Para a conta 20 a transferência é uma entrada.");
        assertEquals(naOrigem.getId(), noDestino.getId());
    }

    @Test
    void extratoVemEmOrdemCronologicaEComDesempatePeloId() {
        Conta conta = contaSalva(10L, ana, "100.00");
        // O id 1 é o mais antigo no banco, mas o mais recente no tempo: a ordem do extrato
        // depende do horário (e, em empate, do id), e não da ordem de gravação.
        repositorio.registrarOperacao(deposito(1L, 10L, "1.00", HORARIO.plusSeconds(3)), List.of(conta));
        repositorio.registrarOperacao(deposito(3L, 10L, "1.00", HORARIO.plusSeconds(1)), List.of(conta));
        repositorio.registrarOperacao(deposito(2L, 10L, "1.00", HORARIO.plusSeconds(1)), List.of(conta));

        assertEquals(List.of(2L, 3L, 1L), ids(repositorio.consultarExtrato(10L)));
    }

    // ---------- erros ----------

    @Test
    void registroInvalidoNoBancoGeraPersistenciaException() throws Exception {
        contaSalva(10L, ana, "100.00");
        executarSql("INSERT INTO transacoes (id, conta_origem, conta_destino, tipo, valor, horario) "
                + "VALUES (1, NULL, 10, 'DEPOSITO', 'abc', '2026-10-23 10:30:00.000000')");

        assertThrows(PersistenciaException.class, () -> repositorio.consultarExtrato(10L));
    }

    @Test
    void construirSemEsquemaGeraPersistenciaException() {
        FabricaConexao semEsquema = FabricaConexao.paraArquivo(pasta.resolve("vazio.db"));

        assertThrows(PersistenciaException.class, () -> new TransacaoRepositorySqlite(semEsquema));
    }

    @Test
    void construirRejeitaFabricaNula() {
        assertThrows(IllegalArgumentException.class, () -> new TransacaoRepositorySqlite(null));
    }

    // ---------- concorrência ----------

    @Test
    void registrosConcorrentesGravamTodasAsTransacoesComIdsDistintos() throws Exception {
        Conta conta = contaSalva(10L, ana, "100.00");
        ExecutorService pool = Executors.newFixedThreadPool(8, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futuros.add(pool.submit(() -> repositorio.registrarOperacao(
                        deposito(repositorio.proximoId(), 10L, "1.00", HORARIO), List.of(conta))));
            }
            for (Future<?> futuro : futuros) {
                futuro.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(100, repositorio.consultarExtrato(10L).size());
        assertEquals(100, contar("transacoes"));
    }
}
