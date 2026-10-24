package domain;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários da raiz de agregado {@link Conta}.
 */
class ContaTest {

    private final Cliente titular = new Cliente(1L, "Ana Souza", "123.456.789-09");
    private final Cliente outroTitular = new Cliente(2L, "Bruno Lima", "987.654.321-00");

    private Conta contaComSaldo(long numero, String saldo) {
        return new Conta(numero, titular, new BigDecimal(saldo));
    }

    private void assertSaldo(String esperado, Conta conta) {
        assertEquals(0, new BigDecimal(esperado).compareTo(conta.consultarSaldo()),
                "Saldo esperado " + esperado + " mas foi " + conta.consultarSaldo());
    }

    // ---------- criação ----------

    @Test
    void contaRejeitaSaldoInicialNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conta(1L, titular, new BigDecimal("-0.01")));
    }

    @Test
    void contaRejeitaSaldoInicialNulo() {
        assertThrows(IllegalArgumentException.class, () -> new Conta(1L, titular, null));
    }

    @Test
    void contaRejeitaTitularNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conta(1L, null, new BigDecimal("10.00")));
    }

    @Test
    void contaRejeitaSaldoInicialComMaisDeDuasCasasDecimais() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conta(1L, titular, new BigDecimal("10.001")));
    }

    @Test
    void contaAceitaSaldoInicialZero() {
        assertSaldo("0.00", contaComSaldo(1L, "0"));
    }

    @Test
    void contaNormalizaSaldoParaDuasCasasDecimais() {
        Conta conta = contaComSaldo(1L, "100");

        assertEquals(2, conta.consultarSaldo().scale());
        assertSaldo("100.00", conta);
    }

    @Test
    void contaGuardaNumeroETitular() {
        Conta conta = contaComSaldo(7L, "50.00");

        assertEquals(7L, conta.getNumero());
        assertEquals(titular, conta.getTitular());
    }

    // ---------- depósito ----------

    @Test
    void depositoValidoAumentaOSaldoExatamente() {
        Conta conta = contaComSaldo(1L, "100.00");

        conta.depositar(new BigDecimal("25.50"));

        assertSaldo("125.50", conta);
    }

    @Test
    void depositosSucessivosSomamSemPerdaDePrecisao() {
        Conta conta = contaComSaldo(1L, "0.00");

        for (int i = 0; i < 10; i++) {
            conta.depositar(new BigDecimal("0.10"));
        }

        assertSaldo("1.00", conta);
    }

    @Test
    void depositoRejeitaValorNulo() {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class, () -> conta.depositar(null));
        assertSaldo("100.00", conta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-50.00", "10.001"})
    void depositoRejeitaValorInvalidoSemAlterarOSaldo(String valorInvalido) {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class,
                () -> conta.depositar(new BigDecimal(valorInvalido)));
        assertSaldo("100.00", conta);
    }

    // ---------- saque ----------

    @Test
    void saqueComSaldoSuficienteReduzOSaldoExatamente() {
        Conta conta = contaComSaldo(1L, "100.00");

        conta.sacar(new BigDecimal("40.25"));

        assertSaldo("59.75", conta);
    }

    @Test
    void saqueDoSaldoIntegralDeixaAContaZerada() {
        Conta conta = contaComSaldo(1L, "100.00");

        conta.sacar(new BigDecimal("100.00"));

        assertSaldo("0.00", conta);
    }

    @Test
    void saqueComSaldoInsuficienteFalhaSemAlterarOSaldo() {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(SaldoInsuficienteException.class,
                () -> conta.sacar(new BigDecimal("100.01")));
        assertSaldo("100.00", conta);
    }

    @Test
    void saqueRejeitaValorNulo() {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class, () -> conta.sacar(null));
        assertSaldo("100.00", conta);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-50.00", "10.001"})
    void saqueRejeitaValorInvalidoSemAlterarOSaldo(String valorInvalido) {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class,
                () -> conta.sacar(new BigDecimal(valorInvalido)));
        assertSaldo("100.00", conta);
    }

    // ---------- transferência ----------

    @Test
    void transferenciaValidaDebitaAOrigemECreditaODestino() {
        Conta origem = contaComSaldo(1L, "100.00");
        Conta destino = new Conta(2L, outroTitular, new BigDecimal("50.00"));

        origem.transferirPara(destino, new BigDecimal("30.00"));

        assertSaldo("70.00", origem);
        assertSaldo("80.00", destino);
    }

    @Test
    void transferenciaRecusadaPorSaldoInsuficienteNaoAlteraNenhumaConta() {
        Conta origem = contaComSaldo(1L, "100.00");
        Conta destino = new Conta(2L, outroTitular, new BigDecimal("50.00"));

        assertThrows(SaldoInsuficienteException.class,
                () -> origem.transferirPara(destino, new BigDecimal("100.01")));

        assertSaldo("100.00", origem);
        assertSaldo("50.00", destino);
    }

    @Test
    void transferenciaRejeitaDestinoNulo() {
        Conta origem = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class,
                () -> origem.transferirPara(null, new BigDecimal("10.00")));
        assertSaldo("100.00", origem);
    }

    @Test
    void transferenciaParaAMesmaContaEhRejeitadaSemAlterarOSaldo() {
        Conta conta = contaComSaldo(1L, "100.00");

        assertThrows(IllegalArgumentException.class,
                () -> conta.transferirPara(conta, new BigDecimal("10.00")));
        assertSaldo("100.00", conta);
    }

    @Test
    void transferenciaRejeitaValorNulo() {
        Conta origem = contaComSaldo(1L, "100.00");
        Conta destino = new Conta(2L, outroTitular, new BigDecimal("50.00"));

        assertThrows(IllegalArgumentException.class, () -> origem.transferirPara(destino, null));
        assertSaldo("100.00", origem);
        assertSaldo("50.00", destino);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-50.00", "10.001"})
    void transferenciaRejeitaValorInvalidoSemAlterarNenhumaConta(String valorInvalido) {
        Conta origem = contaComSaldo(1L, "100.00");
        Conta destino = new Conta(2L, outroTitular, new BigDecimal("50.00"));

        assertThrows(IllegalArgumentException.class,
                () -> origem.transferirPara(destino, new BigDecimal(valorInvalido)));

        assertSaldo("100.00", origem);
        assertSaldo("50.00", destino);
    }

    // ---------- ordem de bloqueio (determinístico) ----------

    @Test
    void transferirPara_BloqueiaPrimeiroAContaDeMenorNumero() throws Exception {
        Conta menor = contaComSaldo(1L, "100.00");
        Conta maior = new Conta(2L, outroTitular, new BigDecimal("100.00"));
        Thread transferencia = new Thread(
                () -> maior.transferirPara(menor, new BigDecimal("1.00")), "transferencia-maior-para-menor");
        transferencia.setDaemon(true);

        // Este teste segura o monitor da conta de MAIOR número. Quem transfere a partir dela
        // só consegue avançar até o ponto em que precisa desse monitor.
        synchronized (maior) {
            transferencia.start();
            long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (transferencia.getState() != Thread.State.BLOCKED) {
                if (System.nanoTime() > limite) {
                    fail("A thread de transferência não chegou a ficar bloqueada.");
                }
                Thread.onSpinWait();
            }

            ThreadInfo info = ManagementFactory.getThreadMXBean()
                    .getThreadInfo(new long[] {transferencia.threadId()}, true, false)[0];
            boolean seguraAContaMenor = Arrays.stream(info.getLockedMonitors())
                    .map(MonitorInfo::getIdentityHashCode)
                    .anyMatch(hash -> hash == System.identityHashCode(menor));

            assertTrue(seguraAContaMenor,
                    "A transferência deve já ter bloqueado a conta de menor número enquanto"
                            + " espera a de maior número.");
        }

        transferencia.join(5_000);
        assertFalse(transferencia.isAlive(), "A transferência deve terminar quando o monitor é liberado.");
        assertSaldo("99.00", maior);
        assertSaldo("101.00", menor);
    }
}