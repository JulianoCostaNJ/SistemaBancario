package app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import domain.SaldoInsuficienteException;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import persistence.ContaRepository;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.PersistenciaException;
import persistence.TransacaoRepository;
import persistence.TransacaoRepositorySqlite;

/**
 * Testes de {@link ServicoBancario}.
 *
 * <p>Os testes de regra usam um banco falso em memória, que também permite simular falhas de
 * gravação. Os testes de integração usam SQLite em arquivo temporário e isolado.</p>
 */
class ServicoBancarioTest {

    private static final Clock RELOGIO =
            Clock.fixed(Instant.parse("2026-10-23T13:30:15.123456789Z"), ZoneOffset.UTC);
    private static final LocalDateTime HORARIO_ESPERADO =
            LocalDateTime.of(2026, 10, 23, 13, 30, 15, 123_456_000);

    @TempDir
    Path pasta;

    private BancoEmMemoria banco;
    private ServicoBancario servico;

    @BeforeEach
    void prepararServico() {
        banco = new BancoEmMemoria();
        servico = new ServicoBancario(banco, banco, RELOGIO);
    }

    private long abrir(String nome, String documento, String saldo) {
        return servico.abrirConta(nome, documento, new BigDecimal(saldo));
    }

    private static void assertSaldo(String esperado, BigDecimal obtido) {
        assertEquals(0, new BigDecimal(esperado).compareTo(obtido),
                "Saldo esperado " + esperado + " mas foi " + obtido);
    }

    private static List<Long> ids(List<Transacao> transacoes) {
        return transacoes.stream().map(Transacao::getId).toList();
    }

    private static ExecutorService criarPool(int threads) {
        return Executors.newFixedThreadPool(threads, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Executa as tarefas em paralelo, liberadas ao mesmo tempo, e aguarda todas terminarem. */
    private static void executarEmParalelo(List<Runnable> tarefas, int threads) throws Exception {
        ExecutorService pool = criarPool(threads);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<?>> futuros = new ArrayList<>();
            for (Runnable tarefa : tarefas) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    tarefa.run();
                    return null;
                }));
            }
            largada.countDown();
            for (Future<?> futuro : futuros) {
                futuro.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- construção ----------

    @Test
    void construtorRejeitaDependenciasNulas() {
        assertThrows(IllegalArgumentException.class, () -> new ServicoBancario(null, banco, RELOGIO));
        assertThrows(IllegalArgumentException.class, () -> new ServicoBancario(banco, null, RELOGIO));
        assertThrows(IllegalArgumentException.class, () -> new ServicoBancario(banco, banco, null));
    }

    // ---------- abrir conta ----------

    @Test
    void abrirContaDevolveNumerosGeradosPeloSistemaEGravaOSaldoInicial() {
        long primeira = abrir("Ana Souza", "111", "100.00");
        long segunda = abrir("Bruno Lima", "222", "0.00");

        assertEquals(1L, primeira);
        assertEquals(2L, segunda);
        assertSaldo("100.00", servico.consultarSaldo(primeira));
        assertSaldo("100.00", banco.saldoGravado(primeira));
    }

    @Test
    void abrirContaNaoCriaTransacaoParaOSaldoInicial() {
        long conta = abrir("Ana Souza", "111", "100.00");

        assertTrue(servico.consultarExtrato(conta).isEmpty());
    }

    @Test
    void abrirContaRejeitaDadosInvalidosSemGravarNada() {
        assertThrows(IllegalArgumentException.class, () -> abrir("  ", "111", "10.00"));
        assertThrows(IllegalArgumentException.class, () -> abrir("Ana", "", "10.00"));
        assertThrows(IllegalArgumentException.class, () -> abrir("Ana", "111", "-0.01"));
        assertThrows(IllegalArgumentException.class, () -> abrir("Ana", "111", "10.001"));
        assertThrows(IllegalArgumentException.class, () -> servico.abrirConta("Ana", "111", null));

        assertEquals(0, banco.quantidadeDeContas());
    }

    @Test
    void abrirContaComDocumentoJaCadastradoEhRecusadaSemAfetarAPrimeira() {
        long primeira = abrir("Ana Souza", "111", "100.00");

        assertThrows(PersistenciaException.class, () -> abrir("Ana Duplicada", "111", "5.00"));

        assertEquals(1, banco.quantidadeDeContas());
        assertSaldo("100.00", servico.consultarSaldo(primeira));
    }

    // ---------- depósito e saque ----------

    @Test
    void depositoAtualizaOSaldoEGravaATransacaoComOHorarioDoRelogio() {
        long conta = abrir("Ana Souza", "111", "100.00");

        Transacao transacao = servico.depositar(conta, new BigDecimal("50.00"));

        assertSaldo("150.00", servico.consultarSaldo(conta));
        assertSaldo("150.00", banco.saldoGravado(conta));
        assertEquals(TipoTransacao.DEPOSITO, transacao.getTipo());
        assertEquals(conta, transacao.getContaDestino());
        assertEquals(HORARIO_ESPERADO, transacao.getHorario());
        assertEquals(List.of(transacao.getId()), ids(servico.consultarExtrato(conta)));
    }

    @Test
    void saqueComSaldoSuficienteReduzOSaldoEGravaATransacao() {
        long conta = abrir("Ana Souza", "111", "100.00");

        Transacao transacao = servico.sacar(conta, new BigDecimal("40.25"));

        assertSaldo("59.75", servico.consultarSaldo(conta));
        assertSaldo("59.75", banco.saldoGravado(conta));
        assertEquals(TipoTransacao.SAQUE, transacao.getTipo());
        assertEquals(conta, transacao.getContaOrigem());
    }

    @Test
    void saqueSemSaldoNaoGravaNadaNemConsomeIdentificador() {
        long conta = abrir("Ana Souza", "111", "10.00");

        assertThrows(SaldoInsuficienteException.class, () -> servico.sacar(conta, new BigDecimal("10.01")));

        assertSaldo("10.00", servico.consultarSaldo(conta));
        assertEquals(0, banco.quantidadeDeGravacoes());
        assertTrue(servico.consultarExtrato(conta).isEmpty());
        assertEquals(1L, servico.depositar(conta, new BigDecimal("1.00")).getId());
    }

    @Test
    void valorInvalidoNaoAlteraNadaNemGrava() {
        long conta = abrir("Ana Souza", "111", "100.00");

        for (String invalido : List.of("0", "-1.00", "10.001")) {
            assertThrows(IllegalArgumentException.class,
                    () -> servico.depositar(conta, new BigDecimal(invalido)));
            assertThrows(IllegalArgumentException.class,
                    () -> servico.sacar(conta, new BigDecimal(invalido)));
        }
        assertThrows(IllegalArgumentException.class, () -> servico.depositar(conta, null));

        assertSaldo("100.00", servico.consultarSaldo(conta));
        assertEquals(0, banco.quantidadeDeGravacoes());
    }

    @Test
    void contaInexistenteEhRejeitadaEmTodasAsOperacoes() {
        long conta = abrir("Ana Souza", "111", "100.00");
        BigDecimal um = new BigDecimal("1.00");

        assertThrows(IllegalArgumentException.class, () -> servico.depositar(99L, um));
        assertThrows(IllegalArgumentException.class, () -> servico.sacar(99L, um));
        assertThrows(IllegalArgumentException.class, () -> servico.transferir(conta, 99L, um));
        assertThrows(IllegalArgumentException.class, () -> servico.transferir(99L, conta, um));
        assertThrows(IllegalArgumentException.class, () -> servico.consultarSaldo(99L));
        assertThrows(IllegalArgumentException.class, () -> servico.consultarExtrato(99L));

        assertSaldo("100.00", servico.consultarSaldo(conta));
        assertEquals(0, banco.quantidadeDeGravacoes());
    }

    // ---------- transferência ----------

    @Test
    void transferenciaAtualizaAsDuasContasEGravaUmaUnicaTransacao() {
        long origem = abrir("Ana Souza", "111", "100.00");
        long destino = abrir("Bruno Lima", "222", "50.00");

        Transacao transacao = servico.transferir(origem, destino, new BigDecimal("30.00"));

        assertSaldo("70.00", servico.consultarSaldo(origem));
        assertSaldo("80.00", servico.consultarSaldo(destino));
        assertSaldo("70.00", banco.saldoGravado(origem));
        assertSaldo("80.00", banco.saldoGravado(destino));
        assertEquals(1, banco.quantidadeDeTransacoes());
        assertEquals(origem, transacao.getContaOrigem());
        assertEquals(destino, transacao.getContaDestino());
        assertEquals(List.of(transacao.getId()), ids(servico.consultarExtrato(origem)));
        assertEquals(List.of(transacao.getId()), ids(servico.consultarExtrato(destino)));
    }

    @Test
    void transferenciaSemSaldoNaoAlteraNenhumaContaENaoGrava() {
        long origem = abrir("Ana Souza", "111", "10.00");
        long destino = abrir("Bruno Lima", "222", "50.00");

        assertThrows(SaldoInsuficienteException.class,
                () -> servico.transferir(origem, destino, new BigDecimal("10.01")));

        assertSaldo("10.00", servico.consultarSaldo(origem));
        assertSaldo("50.00", servico.consultarSaldo(destino));
        assertEquals(0, banco.quantidadeDeGravacoes());
    }

    @Test
    void transferenciaParaAMesmaContaEhRejeitada() {
        long conta = abrir("Ana Souza", "111", "100.00");

        assertThrows(IllegalArgumentException.class,
                () -> servico.transferir(conta, conta, new BigDecimal("1.00")));

        assertSaldo("100.00", servico.consultarSaldo(conta));
        assertEquals(0, banco.quantidadeDeGravacoes());
    }

    // ---------- extrato ----------

    @Test
    void extratoNaoMisturaContasEVemEmOrdemDeGravacao() {
        long a = abrir("Ana Souza", "111", "100.00");
        long b = abrir("Bruno Lima", "222", "100.00");
        long c = abrir("Carla Dias", "333", "100.00");
        servico.depositar(a, new BigDecimal("1.00"));
        servico.depositar(b, new BigDecimal("1.00"));
        servico.transferir(a, c, new BigDecimal("1.00"));

        assertEquals(List.of(1L, 3L), ids(servico.consultarExtrato(a)));
        assertEquals(List.of(2L), ids(servico.consultarExtrato(b)));
        assertEquals(List.of(3L), ids(servico.consultarExtrato(c)));
    }

    // ---------- falhas de gravação ----------

    @Test
    void falhaDeGravacaoDescartaAMemoriaERecarregaOSaldoDoBanco() {
        long conta = abrir("Ana Souza", "111", "100.00");
        banco.falharProximaGravacao();

        assertThrows(PersistenciaException.class,
                () -> servico.depositar(conta, new BigDecimal("50.00")));

        assertSaldo("100.00", servico.consultarSaldo(conta));
        servico.depositar(conta, new BigDecimal("50.00"));
        assertSaldo("150.00", servico.consultarSaldo(conta));
        assertSaldo("150.00", banco.saldoGravado(conta));
    }

    @Test
    void falhaDeGravacaoEmTransferenciaDescartaAsDuasContas() {
        long origem = abrir("Ana Souza", "111", "100.00");
        long destino = abrir("Bruno Lima", "222", "50.00");
        banco.falharProximaGravacao();

        assertThrows(PersistenciaException.class,
                () -> servico.transferir(origem, destino, new BigDecimal("30.00")));

        assertSaldo("100.00", servico.consultarSaldo(origem));
        assertSaldo("50.00", servico.consultarSaldo(destino));
        assertEquals(0, banco.quantidadeDeTransacoes());
    }

    // ---------- recuperação entre execuções ----------

    @Test
    void servicoNovoSobreOMesmoBancoRecuperaContasETransacoes() {
        long origem = abrir("Ana Souza", "111", "100.00");
        long destino = abrir("Bruno Lima", "222", "50.00");
        servico.depositar(origem, new BigDecimal("10.00"));
        servico.transferir(origem, destino, new BigDecimal("30.00"));

        ServicoBancario reiniciado = new ServicoBancario(banco, banco, RELOGIO);

        assertSaldo("80.00", reiniciado.consultarSaldo(origem));
        assertSaldo("80.00", reiniciado.consultarSaldo(destino));
        assertEquals(ids(servico.consultarExtrato(origem)), ids(reiniciado.consultarExtrato(origem)));
        assertEquals(3L, reiniciado.abrirConta("Carla Dias", "333", new BigDecimal("1.00")));
    }

    // ---------- concorrência ----------

    @Test
    void operacoesConcorrentesMantemMemoriaEBancoIdenticos() throws Exception {
        long c1 = abrir("Ana Souza", "111", "1000.00");
        long c2 = abrir("Bruno Lima", "222", "1000.00");
        long c3 = abrir("Carla Dias", "333", "1000.00");
        BigDecimal um = new BigDecimal("1.00");
        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            tarefas.add(() -> servico.depositar(c1, um));
            tarefas.add(() -> servico.sacar(c2, um));
            tarefas.add(() -> servico.transferir(c3, c1, um));
        }

        executarEmParalelo(tarefas, 16);

        assertSaldo("3000.00", servico.consultarSaldo(c1));
        assertSaldo("0.00", servico.consultarSaldo(c2));
        assertSaldo("0.00", servico.consultarSaldo(c3));
        assertSaldo("3000.00", banco.saldoGravado(c1));
        assertSaldo("0.00", banco.saldoGravado(c2));
        assertSaldo("0.00", banco.saldoGravado(c3));
        assertEquals(3000, banco.quantidadeDeTransacoes());
        assertEquals(2000, servico.consultarExtrato(c1).size());
    }

    // ---------- integração com SQLite ----------

    private ServicoBancario servicoSqlite() {
        FabricaConexao fabrica = FabricaConexao.paraArquivo(pasta.resolve("banco.db"));
        fabrica.inicializarSchema();
        return new ServicoBancario(new ContaRepositorySqlite(fabrica),
                new TransacaoRepositorySqlite(fabrica), RELOGIO);
    }

    @Test
    void dadosGravadosNoSqliteSobrevivemAUmaReinicializacao() {
        ServicoBancario primeiro = servicoSqlite();
        long origem = primeiro.abrirConta("Ana Souza", "111", new BigDecimal("100.00"));
        long destino = primeiro.abrirConta("Bruno Lima", "222", new BigDecimal("50.00"));
        primeiro.depositar(origem, new BigDecimal("10.00"));
        primeiro.transferir(origem, destino, new BigDecimal("30.00"));

        ServicoBancario segundo = servicoSqlite();

        assertSaldo("80.00", segundo.consultarSaldo(origem));
        assertSaldo("80.00", segundo.consultarSaldo(destino));
        assertEquals(List.of(1L, 2L), ids(segundo.consultarExtrato(origem)));
        assertEquals(List.of(2L), ids(segundo.consultarExtrato(destino)));
        assertEquals(3L, segundo.abrirConta("Carla Dias", "333", new BigDecimal("1.00")));
    }

    @Test
    void operacoesConcorrentesNoSqliteDeixamMemoriaEBancoIdenticos() {
        ServicoBancario primeiro = servicoSqlite();
        long c1 = primeiro.abrirConta("Ana Souza", "111", new BigDecimal("1000.00"));
        long c2 = primeiro.abrirConta("Bruno Lima", "222", new BigDecimal("1000.00"));
        long c3 = primeiro.abrirConta("Carla Dias", "333", new BigDecimal("1000.00"));
        BigDecimal um = new BigDecimal("1.00");
        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tarefas.add(() -> primeiro.depositar(c1, um));
            tarefas.add(() -> primeiro.sacar(c2, um));
            tarefas.add(() -> primeiro.transferir(c3, c1, um));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> executarEmParalelo(tarefas, 8));

        ServicoBancario segundo = servicoSqlite();
        for (ServicoBancario consulta : List.of(primeiro, segundo)) {
            assertSaldo("1050.00", consulta.consultarSaldo(c1));
            assertSaldo("975.00", consulta.consultarSaldo(c2));
            assertSaldo("975.00", consulta.consultarSaldo(c3));
        }
        assertEquals(50, segundo.consultarExtrato(c1).size());
    }

    @Test
    void transferenciasEmSentidosOpostosNoSqliteTerminamSemDeadlock() {
        ServicoBancario primeiro = servicoSqlite();
        long a = primeiro.abrirConta("Ana Souza", "111", new BigDecimal("1000.00"));
        long b = primeiro.abrirConta("Bruno Lima", "222", new BigDecimal("1000.00"));
        BigDecimal um = new BigDecimal("1.00");
        List<Runnable> tarefas = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            tarefas.add(() -> primeiro.transferir(a, b, um));
            tarefas.add(() -> primeiro.transferir(b, a, um));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> executarEmParalelo(tarefas, 8));

        ServicoBancario segundo = servicoSqlite();
        assertSaldo("1000.00", primeiro.consultarSaldo(a));
        assertSaldo("1000.00", segundo.consultarSaldo(a));
        assertSaldo("1000.00", segundo.consultarSaldo(b));
    }

    // ---------- banco falso em memória ----------

    /**
     * Banco falso que implementa os dois contratos de persistência. Guarda cópias dos dados (e não
     * os objetos do serviço), como um banco de verdade, e permite simular uma falha de gravação.
     */
    private static final class BancoEmMemoria implements ContaRepository, TransacaoRepository {

        private record RegistroDeConta(long numero, Cliente titular, BigDecimal saldo) {
        }

        private final Map<Long, RegistroDeConta> contas = new TreeMap<>();
        private final List<Transacao> transacoes = new ArrayList<>();
        private final AtomicLong ultimoNumero = new AtomicLong();
        private final AtomicLong ultimoIdCliente = new AtomicLong();
        private final AtomicLong ultimoIdTransacao = new AtomicLong();
        private boolean falharProximaGravacao;
        private int gravacoes;

        synchronized void falharProximaGravacao() {
            falharProximaGravacao = true;
        }

        synchronized int quantidadeDeContas() {
            return contas.size();
        }

        synchronized int quantidadeDeTransacoes() {
            return transacoes.size();
        }

        synchronized int quantidadeDeGravacoes() {
            return gravacoes;
        }

        synchronized BigDecimal saldoGravado(long numero) {
            return contas.get(numero).saldo();
        }

        @Override
        public synchronized void inserir(Conta conta) {
            Cliente titular = conta.getTitular();
            if (contas.containsKey(conta.getNumero())) {
                throw new PersistenciaException("Conta repetida.");
            }
            boolean documentoDeOutroCliente = contas.values().stream()
                    .map(RegistroDeConta::titular)
                    .anyMatch(outro -> outro.getDocumento().equals(titular.getDocumento())
                            && outro.getId() != titular.getId());
            if (documentoDeOutroCliente) {
                throw new PersistenciaException("Documento repetido.");
            }
            contas.put(conta.getNumero(),
                    new RegistroDeConta(conta.getNumero(), titular, conta.consultarSaldo()));
            ultimoNumero.accumulateAndGet(conta.getNumero(), Math::max);
            ultimoIdCliente.accumulateAndGet(titular.getId(), Math::max);
        }

        @Override
        public long proximoNumeroConta() {
            return ultimoNumero.incrementAndGet();
        }

        @Override
        public long proximoIdCliente() {
            return ultimoIdCliente.incrementAndGet();
        }

        @Override
        public synchronized Optional<Conta> buscarPorNumero(long numero) {
            return Optional.ofNullable(contas.get(numero))
                    .map(registro -> new Conta(registro.numero(), registro.titular(), registro.saldo()));
        }

        @Override
        public synchronized List<Conta> listarTodas() {
            return contas.values().stream()
                    .map(registro -> new Conta(registro.numero(), registro.titular(), registro.saldo()))
                    .toList();
        }

        @Override
        public long proximoId() {
            return ultimoIdTransacao.incrementAndGet();
        }

        @Override
        public synchronized void registrarOperacao(Transacao transacao, Collection<Conta> afetadas) {
            if (falharProximaGravacao) {
                falharProximaGravacao = false;
                throw new PersistenciaException("Falha simulada de gravação.");
            }
            for (Conta conta : afetadas) {
                if (!contas.containsKey(conta.getNumero())) {
                    throw new PersistenciaException("Conta não gravada.");
                }
            }
            for (Conta conta : afetadas) {
                RegistroDeConta antigo = contas.get(conta.getNumero());
                contas.put(conta.getNumero(),
                        new RegistroDeConta(antigo.numero(), antigo.titular(), conta.consultarSaldo()));
            }
            transacoes.add(transacao);
            gravacoes++;
        }

        @Override
        public synchronized List<Transacao> consultarExtrato(long numeroConta) {
            return transacoes.stream()
                    .filter(t -> Long.valueOf(numeroConta).equals(t.getContaOrigem())
                            || Long.valueOf(numeroConta).equals(t.getContaDestino()))
                    .sorted(Comparator.comparing(Transacao::getHorario).thenComparing(Transacao::getId))
                    .toList();
        }
    }
}
