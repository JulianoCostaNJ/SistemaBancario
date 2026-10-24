package persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes de {@link ContaRepositorySqlite} contra um banco SQLite em arquivo temporário e isolado.
 */
class ContaRepositorySqliteTest {

    @TempDir
    Path pasta;

    private FabricaConexao fabrica;
    private ContaRepositorySqlite repositorio;

    private final Cliente ana = new Cliente(1L, "Ana Souza", "123.456.789-09");
    private final Cliente bruno = new Cliente(2L, "Bruno Lima", "987.654.321-00");

    @BeforeEach
    void prepararBanco() {
        fabrica = FabricaConexao.paraArquivo(pasta.resolve("teste.db"));
        fabrica.inicializarSchema();
        repositorio = new ContaRepositorySqlite(fabrica);
    }

    private Conta conta(long numero, Cliente titular, String saldo) {
        return new Conta(numero, titular, new BigDecimal(saldo));
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

    // ---------- salvar e recuperar ----------

    @Test
    void contaSalvaEhRecuperadaComTitularENumeroESaldo() {
        repositorio.salvar(conta(10L, ana, "250.75"));

        Optional<Conta> recuperada = repositorio.buscarPorNumero(10L);

        assertTrue(recuperada.isPresent());
        assertEquals(10L, recuperada.get().getNumero());
        assertEquals(1L, recuperada.get().getTitular().getId());
        assertEquals("Ana Souza", recuperada.get().getTitular().getNome());
        assertEquals("123.456.789-09", recuperada.get().getTitular().getDocumento());
        assertEquals(0, new BigDecimal("250.75").compareTo(recuperada.get().consultarSaldo()));
        assertEquals(2, recuperada.get().consultarSaldo().scale());
    }

    @Test
    void saldoGrandeEhPreservadoSemPerdaDePrecisao() {
        repositorio.salvar(conta(10L, ana, "12345678901234567.89"));

        Conta recuperada = repositorio.buscarPorNumero(10L).orElseThrow();

        assertEquals(new BigDecimal("12345678901234567.89"), recuperada.consultarSaldo());
    }

    @Test
    void saldoEhGravadoComoTextoEExatoNoBanco() throws Exception {
        repositorio.salvar(conta(10L, ana, "0.10"));

        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT typeof(saldo), saldo FROM contas WHERE numero = 10")) {
            assertTrue(resultado.next());
            assertEquals("text", resultado.getString(1));
            assertEquals("0.10", resultado.getString(2));
        }
    }

    @Test
    void buscarContaInexistenteDevolveVazio() {
        assertFalse(repositorio.buscarPorNumero(999L).isPresent());
    }

    // ---------- atualizar ----------

    @Test
    void salvarContaExistenteAtualizaOSaldoSemDuplicar() {
        Conta conta = conta(10L, ana, "100.00");
        repositorio.salvar(conta);
        conta.depositar(new BigDecimal("50.25"));

        repositorio.salvar(conta);

        assertEquals(1, repositorio.listarTodas().size());
        assertEquals(0, new BigDecimal("150.25")
                .compareTo(repositorio.buscarPorNumero(10L).orElseThrow().consultarSaldo()));
    }

    @Test
    void duasContasDoMesmoTitularCompartilhamUmUnicoCliente() throws Exception {
        repositorio.salvar(conta(10L, ana, "100.00"));
        repositorio.salvar(conta(11L, ana, "200.00"));

        assertEquals(1, contar("clientes"));
        assertEquals(2, contar("contas"));
        assertEquals(1L, repositorio.buscarPorNumero(11L).orElseThrow().getTitular().getId());
    }

    // ---------- listar ----------

    @Test
    void listarTodasSemContasDevolveListaVazia() {
        assertTrue(repositorio.listarTodas().isEmpty());
    }

    @Test
    void listarTodasDevolveAsContasEmOrdemCrescenteDeNumero() {
        repositorio.salvar(conta(30L, bruno, "3.00"));
        repositorio.salvar(conta(10L, ana, "1.00"));
        repositorio.salvar(conta(20L, ana, "2.00"));

        List<Long> numeros = repositorio.listarTodas().stream().map(Conta::getNumero).toList();

        assertEquals(List.of(10L, 20L, 30L), numeros);
    }

    // ---------- erros ----------

    @Test
    void salvarRejeitaContaNula() {
        assertThrows(IllegalArgumentException.class, () -> repositorio.salvar(null));
    }

    @Test
    void falhaNaGravacaoDaContaDesfazAGravacaoDoTitular() throws Exception {
        executarSql("DROP TABLE contas");

        assertThrows(PersistenciaException.class, () -> repositorio.salvar(conta(10L, ana, "100.00")));

        assertEquals(0, contar("clientes"), "O titular não pode ficar gravado sem a conta.");
    }

    @Test
    void registroComSaldoInvalidoNoBancoGeraPersistenciaException() throws Exception {
        executarSql("INSERT INTO clientes (id, nome, documento) VALUES (1, 'Ana', '123')");
        executarSql("INSERT INTO contas (numero, cliente_id, saldo) VALUES (10, 1, 'abc')");

        assertThrows(PersistenciaException.class, () -> repositorio.buscarPorNumero(10L));
        assertThrows(PersistenciaException.class, () -> repositorio.listarTodas());
    }

    // ---------- concorrência ----------

    @Test
    void salvamentosConcorrentesDeContasDiferentesSaoTodosPersistidos() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        });
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (long numero = 1; numero <= 40; numero++) {
                Conta conta = conta(numero, numero % 2 == 0 ? ana : bruno, numero + ".00");
                futuros.add(pool.submit(() -> repositorio.salvar(conta)));
            }
            for (Future<?> futuro : futuros) {
                futuro.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(40, repositorio.listarTodas().size());
        assertEquals(2, contar("clientes"));
    }
}
