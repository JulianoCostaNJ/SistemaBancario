package domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários da raiz de agregado {@link Conta} (Semana 1: domínio sequencial).
 */
class ContaTest {

    private final Cliente titular = new Cliente(1L, "Ana Souza", "123.456.789-09");

    @Test
    void contaRejeitaSaldoInicialNegativo() {
        assertThrows(IllegalArgumentException.class,
                () -> new Conta(1L, titular, new BigDecimal("-0.01")));
    }
}
