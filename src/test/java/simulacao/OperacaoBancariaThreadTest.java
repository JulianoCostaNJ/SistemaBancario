package simulacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * Testes de {@link OperacaoBancariaThread}: comportamento unitário e cenários concorrentes.
 *
 * <p>Os cenários concorrentes usam {@link CountDownLatch} para largada simultânea e verificam
 * saldos finais exatos, calculados a partir das operações efetivamente concluídas.</p>
 */
class OperacaoBancariaThreadTest {

    private static final BigDecimal UM_REAL = new BigDecimal("1.00");
    private static final int THREADS = 16;

    private final Cliente titular = new Cliente(1L, "Ana Souza", "123.456.789-09");

    private Conta conta(long numero, String saldo) {
        return new Conta(numero, titular, new BigDecimal(saldo));
    }

    private static void assertSaldo(String esperado, Conta conta) {
        assertEquals(0, new BigDecimal(esperado).compareTo(conta.consultarSaldo()),
                "Saldo esperado " + esperado + " mas foi " + conta.consultarSaldo());
    }

    /**
     * Executa as operações em um pool de threads, todas liberadas ao mesmo tempo por uma
     * largada única, e aguarda o término de todas (com limite de tempo).
     *
     * @param operacoes operações a executar
     * @param numeroDeThreads tamanho do pool
     * @throws Exception se alguma operação falhar ou exceder o limite de tempo
     */
    static void executar(List<OperacaoBancariaThread> operacoes, int numeroDeThreads)
            throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(numeroDeThreads, tarefa -> {
            Thread thread = new Thread(tarefa);
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch largada = new CountDownLatch(1);
        try {
            List<Future<Object>> futuros = new ArrayList<>();
            for (OperacaoBancariaThread operacao : operacoes) {
                futuros.add(pool.submit(() -> {
                    largada.await();
                    operacao.run();
                    return null;
                }));
            }
            largada.countDown();
            for (Future<Object> futuro : futuros) {
                futuro.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private static long contarConcluidas(List<OperacaoBancariaThread> operacoes) {
        return operacoes.stream().filter(OperacaoBancariaThread::foiConcluida).count();
    }

    // ---------- comportamento unitário (determinístico) ----------

    @Test
    void threadRejeitaTipoNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(null, null, conta(1L, "10.00"), UM_REAL));
    }

    @Test
    void threadRejeitaValorNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, conta(1L, "10.00"), null));
    }

    @Test
    void threadRejeitaContaDestinoNulaNoDeposito() {
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, null, UM_REAL));
    }

    @Test
    void threadRejeitaContaOrigemNulaNoSaque() {
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(TipoTransacao.SAQUE, null, null, UM_REAL));
    }

    @Test
    void threadRejeitaContaNulaNaTransferencia() {
        Conta origem = conta(1L, "10.00");
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA, origem, null, UM_REAL));
        assertThrows(IllegalArgumentException.class,
                () -> new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA, null, origem, UM_REAL));
    }

    @Test
    void threadNaoExecutadaNaoEstaConcluidaNemRejeitada() {
        OperacaoBancariaThread operacao =
                new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, conta(1L, "10.00"), UM_REAL);

        assertFalse(operacao.foiConcluida());
        assertFalse(operacao.foiRejeitada());
    }

    @Test
    void threadExecutaDeposito() {
        Conta destino = conta(1L, "10.00");
        OperacaoBancariaThread operacao =
                new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, destino, new BigDecimal("5.50"));

        operacao.run();

        assertTrue(operacao.foiConcluida());
        assertFalse(operacao.foiRejeitada());
        assertSaldo("15.50", destino);
    }

    @Test
    void threadExecutaTransferencia() {
        Conta origem = conta(1L, "10.00");
        Conta destino = conta(2L, "0.00");
        OperacaoBancariaThread operacao =
                new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA, origem, destino, new BigDecimal("4.00"));

        operacao.run();

        assertTrue(operacao.foiConcluida());
        assertSaldo("6.00", origem);
        assertSaldo("4.00", destino);
    }

    @Test
    void threadMarcaSaqueSemSaldoComoRejeitadoSemAlterarOSaldo() {
        Conta origem = conta(1L, "1.00");
        OperacaoBancariaThread operacao =
                new OperacaoBancariaThread(TipoTransacao.SAQUE, origem, null, new BigDecimal("2.00"));

        operacao.run();

        assertFalse(operacao.foiConcluida());
        assertTrue(operacao.foiRejeitada());
        assertSaldo("1.00", origem);
    }

    // ---------- cenários concorrentes ----------

    @Test
    void depositosConcorrentesNaMesmaContaGeramSaldoFinalExato() throws Exception {
        Conta destino = conta(1L, "0.00");
        List<OperacaoBancariaThread> operacoes = new ArrayList<>();
        for (int i = 0; i < 20_000; i++) {
            operacoes.add(new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, destino, UM_REAL));
        }

        executar(operacoes, THREADS);

        assertEquals(20_000, contarConcluidas(operacoes));
        assertSaldo("20000.00", destino);
    }

    @Test
    void saquesConcorrentesRespeitamOSaldoEContamAsRejeicoes() throws Exception {
        Conta origem = conta(1L, "5000.00");
        List<OperacaoBancariaThread> operacoes = new ArrayList<>();
        for (int i = 0; i < 8_000; i++) {
            operacoes.add(new OperacaoBancariaThread(TipoTransacao.SAQUE, origem, null, UM_REAL));
        }

        executar(operacoes, THREADS);

        long concluidas = contarConcluidas(operacoes);
        assertEquals(5_000, concluidas, "Só 5000 saques de R$ 1,00 cabem no saldo");
        assertSaldo("0.00", origem);
    }

    @Test
    void transferenciasEmSentidosOpostosTerminamSemDeadlockEPreservamOsSaldos() {
        Conta a = conta(1L, "10000.00");
        Conta b = conta(2L, "10000.00");
        List<OperacaoBancariaThread> operacoes = new ArrayList<>();
        for (int i = 0; i < 5_000; i++) {
            operacoes.add(new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA, a, b, UM_REAL));
            operacoes.add(new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA, b, a, UM_REAL));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> executar(operacoes, THREADS));

        assertEquals(10_000, contarConcluidas(operacoes));
        assertSaldo("10000.00", a);
        assertSaldo("10000.00", b);
    }

    @Test
    void transferenciasEmAneisEntreTresContasPreservamATotalidadeDoDinheiro() {
        Conta c1 = conta(1L, "1000.00");
        Conta c2 = conta(2L, "1000.00");
        Conta c3 = conta(3L, "1000.00");
        Conta[] contas = {c1, c2, c3};
        List<OperacaoBancariaThread> operacoes = new ArrayList<>();
        for (int i = 0; i < 6_000; i++) {
            operacoes.add(new OperacaoBancariaThread(TipoTransacao.TRANSFERENCIA,
                    contas[i % 3], contas[(i + 1) % 3], UM_REAL));
        }

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> executar(operacoes, THREADS));

        assertEquals(6_000, contarConcluidas(operacoes));
        assertSaldo("1000.00", c1);
        assertSaldo("1000.00", c2);
        assertSaldo("1000.00", c3);
    }
}
