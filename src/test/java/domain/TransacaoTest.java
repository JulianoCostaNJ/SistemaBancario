package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários do registro imutável {@link Transacao}.
 */
class TransacaoTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 23, 10, 30);
    private static final BigDecimal VALOR = new BigDecimal("125.50");

    @Test
    void depositoGuardaSeusDadosSemContaOrigem() {
        Transacao transacao = new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO, VALOR, HORARIO);

        assertEquals(1L, transacao.getId());
        assertNull(transacao.getContaOrigem());
        assertEquals(10L, transacao.getContaDestino());
        assertEquals(TipoTransacao.DEPOSITO, transacao.getTipo());
        assertEquals(0, VALOR.compareTo(transacao.getValor()));
        assertEquals(HORARIO, transacao.getHorario());
    }

    @Test
    void saqueGuardaSeusDadosSemContaDestino() {
        Transacao transacao = new Transacao(2L, 10L, null, TipoTransacao.SAQUE, VALOR, HORARIO);

        assertEquals(10L, transacao.getContaOrigem());
        assertNull(transacao.getContaDestino());
        assertEquals(TipoTransacao.SAQUE, transacao.getTipo());
    }

    @Test
    void transferenciaGuardaContaOrigemEContaDestino() {
        Transacao transacao = new Transacao(3L, 10L, 20L, TipoTransacao.TRANSFERENCIA, VALOR, HORARIO);

        assertEquals(10L, transacao.getContaOrigem());
        assertEquals(20L, transacao.getContaDestino());
        assertEquals(TipoTransacao.TRANSFERENCIA, transacao.getTipo());
    }

    @Test
    void valorEhNormalizadoParaDuasCasasDecimais() {
        Transacao transacao = new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO,
                new BigDecimal("100"), HORARIO);

        assertEquals(2, transacao.getValor().scale());
    }

    @Test
    void transacaoRejeitaTipoNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 10L, null, VALOR, HORARIO));
    }

    @Test
    void transacaoRejeitaHorarioNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO, VALOR, null));
    }

    @Test
    void transacaoRejeitaValorNulo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO, null, HORARIO));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-0.01", "-100.00"})
    void transacaoRejeitaValorZeroOuNegativo(String valorInvalido) {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO,
                        new BigDecimal(valorInvalido), HORARIO));
    }

    @Test
    void transacaoRejeitaValorComMaisDeDuasCasasDecimais() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 10L, TipoTransacao.DEPOSITO,
                        new BigDecimal("10.001"), HORARIO));
    }

    @Test
    void depositoRejeitaContaOrigem() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, 5L, 10L, TipoTransacao.DEPOSITO, VALOR, HORARIO));
    }

    @Test
    void depositoExigeContaDestino() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, null, TipoTransacao.DEPOSITO, VALOR, HORARIO));
    }

    @Test
    void saqueRejeitaContaDestino() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, 10L, 5L, TipoTransacao.SAQUE, VALOR, HORARIO));
    }

    @Test
    void saqueExigeContaOrigem() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, null, TipoTransacao.SAQUE, VALOR, HORARIO));
    }

    @Test
    void transferenciaExigeContaOrigem() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, null, 20L, TipoTransacao.TRANSFERENCIA, VALOR, HORARIO));
    }

    @Test
    void transferenciaExigeContaDestino() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, 10L, null, TipoTransacao.TRANSFERENCIA, VALOR, HORARIO));
    }

    @Test
    void transferenciaRejeitaOrigemIgualAoDestino() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transacao(1L, 10L, 10L, TipoTransacao.TRANSFERENCIA, VALOR, HORARIO));
    }
}
