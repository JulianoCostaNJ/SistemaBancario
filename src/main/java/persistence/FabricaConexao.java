package persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Centraliza a criação de conexões com o banco SQLite e a inicialização do esquema.
 *
 * <p>Cada chamada de {@link #criarConexao()} devolve uma conexão nova, que nunca deve ser
 * compartilhada entre threads. As configurações do SQLite que valem por conexão (chaves
 * estrangeiras e tempo de espera por banco ocupado) são aplicadas aqui, para que nenhuma conexão
 * seja usada sem elas.</p>
 */
public final class FabricaConexao {

    private static final int BUSY_TIMEOUT_MS = 5_000;
    private static final String RECURSO_SCHEMA = "/schema.sql";

    private final String url;

    /**
     * Cria uma fábrica para a URL JDBC informada.
     *
     * @param url URL JDBC do banco, por exemplo {@code jdbc:sqlite:poo-bank.db}
     * @throws IllegalArgumentException se a URL for nula ou em branco
     */
    public FabricaConexao(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A URL do banco é obrigatória.");
        }
        this.url = url;
    }

    /**
     * Cria uma fábrica para um banco SQLite em arquivo.
     *
     * <p>Não use bancos em memória com várias conexões: no SQLite, cada conexão em memória
     * enxerga um banco diferente.</p>
     *
     * @param arquivo caminho do arquivo do banco, criado se não existir
     * @return fábrica de conexões para o arquivo
     */
    public static FabricaConexao paraArquivo(Path arquivo) {
        return new FabricaConexao("jdbc:sqlite:" + arquivo.toAbsolutePath());
    }

    /**
     * Abre uma nova conexão, com as chaves estrangeiras ativadas e o tempo de espera por banco
     * ocupado configurado. Quem recebe a conexão deve fechá-la.
     *
     * @return conexão nova e configurada
     * @throws PersistenciaException se a conexão não puder ser aberta ou configurada
     */
    public Connection criarConexao() {
        try {
            Connection conexao = DriverManager.getConnection(url);
            try {
                configurar(conexao);
            } catch (SQLException e) {
                try {
                    conexao.close();
                } catch (SQLException falhaAoFechar) {
                    e.addSuppressed(falhaAoFechar);
                }
                throw e;
            }
            return conexao;
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível abrir a conexão com o banco.", e);
        }
    }

    /**
     * Cria as tabelas e os índices definidos em {@code schema.sql}, se ainda não existirem. Pode
     * ser executado mais de uma vez com segurança. O esquema é criado em uma única transação.
     *
     * @throws PersistenciaException se o esquema não puder ser lido ou aplicado
     */
    public void inicializarSchema() {
        String[] comandos = lerComandosDoSchema();
        try (Connection conexao = criarConexao()) {
            conexao.setAutoCommit(false);
            try (Statement declaracao = conexao.createStatement()) {
                for (String comando : comandos) {
                    declaracao.execute(comando);
                }
                conexao.commit();
            } catch (SQLException e) {
                try {
                    conexao.rollback();
                } catch (SQLException falhaNoRollback) {
                    e.addSuppressed(falhaNoRollback);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível criar o esquema do banco.", e);
        }
    }

    private static void configurar(Connection conexao) throws SQLException {
        try (Statement declaracao = conexao.createStatement()) {
            declaracao.execute("PRAGMA foreign_keys = ON");
            declaracao.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
        }
    }

    private static String[] lerComandosDoSchema() {
        try (InputStream entrada = FabricaConexao.class.getResourceAsStream(RECURSO_SCHEMA)) {
            if (entrada == null) {
                throw new PersistenciaException("O arquivo schema.sql não foi encontrado.");
            }
            String script = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
            String semComentarios = script.lines()
                    .filter(linha -> !linha.strip().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(semComentarios.split(";"))
                    .map(String::strip)
                    .filter(comando -> !comando.isEmpty())
                    .toArray(String[]::new);
        } catch (IOException e) {
            throw new PersistenciaException("Não foi possível ler o arquivo schema.sql.", e);
        }
    }
}
