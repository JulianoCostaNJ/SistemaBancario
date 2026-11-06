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
 *
 * <p>O esquema tem dois scripts: {@code schema.sql}, o núcleo exigido pela especificação (clientes,
 * contas e transações), e {@code schema-banco-digital.sql}, a extensão do banco digital (acesso,
 * categorias, Pix, cartões, faturas e metas), que só referencia o núcleo.</p>
 */
public final class FabricaConexao {

    private static final int BUSY_TIMEOUT_MS = 5_000;
    private static final String RECURSO_SCHEMA = "/schema.sql";
    private static final String RECURSO_SCHEMA_BANCO_DIGITAL = "/schema-banco-digital.sql";
    private static final String RECURSO_DADOS_BANCO_DIGITAL = "/seed-banco-digital.sql";
    private static final String ERRO_ESQUEMA = "Não foi possível criar o esquema do banco.";
    private static final String ERRO_DADOS = "Não foi possível gravar os dados do banco digital.";

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
     * Cria as tabelas e os índices do núcleo, definidos em {@code schema.sql}, se ainda não
     * existirem. Pode ser executado mais de uma vez com segurança. O esquema é criado em uma única
     * transação.
     *
     * @throws PersistenciaException se o esquema não puder ser lido ou aplicado
     */
    public void inicializarSchema() {
        aplicarScripts(ERRO_ESQUEMA, RECURSO_SCHEMA);
    }

    /**
     * Cria o núcleo ({@code schema.sql}) e a extensão do banco digital
     * ({@code schema-banco-digital.sql}), nessa ordem e em uma única transação. Se qualquer
     * comando falhar, nada é criado. Pode ser executado mais de uma vez com segurança.
     *
     * @throws PersistenciaException se algum script não puder ser lido ou aplicado
     */
    public void inicializarSchemaCompleto() {
        aplicarScripts(ERRO_ESQUEMA, RECURSO_SCHEMA, RECURSO_SCHEMA_BANCO_DIGITAL);
    }

    /**
     * Grava os dados de demonstração do banco digital ({@code seed-banco-digital.sql}): categorias,
     * identificação exibida das contas, credenciais, chaves Pix, cartões, faturas, lançamentos,
     * detalhes das transações, pagamentos de fatura e metas.
     *
     * <p>As contas e as transações precisam existir antes: este método não grava saldo nem
     * transações, apenas dados que apontam para elas. Tudo ocorre em uma única transação, então se
     * faltar alguma conta nada é gravado. Pode ser executado mais de uma vez com segurança.</p>
     *
     * @throws PersistenciaException se o script não puder ser lido ou aplicado, inclusive quando as
     *         contas de demonstração ainda não existirem
     */
    public void popularDadosBancoDigital() {
        aplicarScripts(ERRO_DADOS, RECURSO_DADOS_BANCO_DIGITAL);
    }

    private void aplicarScripts(String mensagemDeErro, String... recursos) {
        String[][] comandosPorScript = new String[recursos.length][];
        for (int i = 0; i < recursos.length; i++) {
            comandosPorScript[i] = lerComandosDoScript(recursos[i]);
        }
        try (Connection conexao = criarConexao()) {
            conexao.setAutoCommit(false);
            try (Statement declaracao = conexao.createStatement()) {
                for (String[] comandos : comandosPorScript) {
                    for (String comando : comandos) {
                        declaracao.execute(comando);
                    }
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
            throw new PersistenciaException(mensagemDeErro, e);
        }
    }

    private static void configurar(Connection conexao) throws SQLException {
        try (Statement declaracao = conexao.createStatement()) {
            declaracao.execute("PRAGMA foreign_keys = ON");
            declaracao.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
        }
    }

    private static String[] lerComandosDoScript(String recurso) {
        try (InputStream entrada = FabricaConexao.class.getResourceAsStream(recurso)) {
            if (entrada == null) {
                throw new PersistenciaException(
                        "O arquivo " + recurso.substring(1) + " não foi encontrado.");
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
            throw new PersistenciaException(
                    "Não foi possível ler o arquivo " + recurso.substring(1) + ".", e);
        }
    }
}
