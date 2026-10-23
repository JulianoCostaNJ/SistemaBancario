package domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Testes unitários da entidade {@link Cliente}.
 */
class ClienteTest {

    @Test
    void clienteValidoGuardaSeusDados() {
        Cliente cliente = new Cliente(1L, "Ana Souza", "123.456.789-09");

        assertEquals(1L, cliente.getId());
        assertEquals("Ana Souza", cliente.getNome());
        assertEquals("123.456.789-09", cliente.getDocumento());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void clienteRejeitaNomeNuloOuEmBranco(String nomeInvalido) {
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1L, nomeInvalido, "123.456.789-09"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void clienteRejeitaDocumentoNuloOuEmBranco(String documentoInvalido) {
        assertThrows(IllegalArgumentException.class,
                () -> new Cliente(1L, "Ana Souza", documentoInvalido));
    }
}
