package persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes de {@link FabricaConexao} contra um banco SQLite em arquivo temporário e isolado.
 */
class FabricaConexaoTest {

    @TempDir
    Path pasta;

    private FabricaConexao fabrica() {
        return FabricaConexao.paraArquivo(pasta.resolve("teste.db"));
    }

    private static int lerInteiro(Statement comando, String pragma) throws SQLException {
        try (ResultSet resultado = comando.executeQuery(pragma)) {
            assertTrue(resultado.next(), "O PRAGMA deveria retornar uma linha: " + pragma);
            return resultado.getInt(1);
        }
    }

    private static List<String> listarTabelas(FabricaConexao fabrica) throws SQLException {
        List<String> tabelas = new ArrayList<>();
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' "
                             + "AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (resultado.next()) {
                tabelas.add(resultado.getString(1));
            }
        }
        return tabelas;
    }

    @Test
    void conexaoTemChavesEstrangeirasEBusyTimeoutConfigurados() throws Exception {
        try (Connection conexao = fabrica().criarConexao();
             Statement comando = conexao.createStatement()) {
            assertEquals(1, lerInteiro(comando, "PRAGMA foreign_keys"));
            assertEquals(5000, lerInteiro(comando, "PRAGMA busy_timeout"));
        }
    }

    @Test
    void inicializarSchemaCriaAsTresTabelas() throws Exception {
        FabricaConexao fabrica = fabrica();

        fabrica.inicializarSchema();

        assertEquals(List.of("clientes", "contas", "transacoes"), listarTabelas(fabrica));
    }

    @Test
    void inicializarSchemaPodeSerExecutadoMaisDeUmaVez() {
        FabricaConexao fabrica = fabrica();
        fabrica.inicializarSchema();

        assertDoesNotThrow(fabrica::inicializarSchema);
    }

    @Test
    void chaveEstrangeiraInexistenteEhRejeitadaPeloBanco() {
        FabricaConexao fabrica = fabrica();
        fabrica.inicializarSchema();

        assertThrows(SQLException.class, () -> {
            try (Connection conexao = fabrica.criarConexao();
                 Statement comando = conexao.createStatement()) {
                comando.executeUpdate(
                        "INSERT INTO contas (numero, cliente_id, saldo) VALUES (1, 999, '10.00')");
            }
        });
    }

    @Test
    void caminhoInvalidoGeraPersistenciaException() {
        FabricaConexao fabrica =
                FabricaConexao.paraArquivo(pasta.resolve("pasta-inexistente").resolve("x.db"));

        assertThrows(PersistenciaException.class, fabrica::criarConexao);
    }

    @Test
    void urlNulaOuEmBrancoEhRejeitada() {
        assertThrows(IllegalArgumentException.class, () -> new FabricaConexao(null));
        assertThrows(IllegalArgumentException.class, () -> new FabricaConexao("   "));
    }
}
