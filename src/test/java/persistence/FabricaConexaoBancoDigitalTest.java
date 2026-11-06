package persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes da extensão do esquema ({@code schema-banco-digital.sql}) contra um SQLite em arquivo
 * temporário e isolado.
 */
class FabricaConexaoBancoDigitalTest {

    private static final String CARTAO_VALIDO = cartao(1, "VISA", "4821", "5000.00", 1);

    private static String cartao(int id, String bandeira, String finalCartao, String limite,
                                 int principal) {
        return "INSERT INTO cartoes (id, conta_numero, bandeira, produto, apelido, final_cartao, "
                + "nome_impresso, validade, limite_total, dia_fechamento, dia_vencimento, principal, "
                + "status) VALUES (" + id + ", 10, '" + bandeira + "', 'Platinum', 'Principal', '"
                + finalCartao + "', 'ANA SOUZA', '12/26', '" + limite + "', 12, 19, " + principal
                + ", 'ATIVO')";
    }

    @TempDir
    Path pasta;

    private FabricaConexao fabrica;

    @BeforeEach
    void prepararBanco() throws SQLException {
        fabrica = FabricaConexao.paraArquivo(pasta.resolve("teste.db"));
        fabrica.inicializarSchemaCompleto();
        executar("INSERT INTO clientes VALUES (1, 'Ana Souza', '123.456.789-09')");
        executar("INSERT INTO contas VALUES (10, 1, '100.00')");
        executar("INSERT INTO transacoes VALUES (1, NULL, 10, 'DEPOSITO', '5.00', "
                + "'2026-11-05 10:20:00.000000')");
    }

    private void executar(String sql) throws SQLException {
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement()) {
            comando.executeUpdate(sql);
        }
    }

    private Set<String> listarTabelas() throws SQLException {
        Set<String> tabelas = new TreeSet<>();
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table' "
                             + "AND name NOT LIKE 'sqlite_%'")) {
            while (resultado.next()) {
                tabelas.add(resultado.getString(1));
            }
        }
        return tabelas;
    }

    private void assertRejeitado(String sql) {
        assertThrows(SQLException.class, () -> executar(sql));
    }

    @Test
    void inicializarSchemaCompletoCriaNucleoEExtensao() throws Exception {
        assertEquals(new TreeSet<>(Set.of("clientes", "contas", "transacoes", "credenciais",
                "dados_conta", "categorias", "transacao_detalhes", "chaves_pix", "pix_operacoes", "cartoes",
                "faturas", "lancamentos_cartao", "pagamentos_fatura", "metas")), listarTabelas());
    }

    @Test
    void inicializarSchemaCompletoPodeSerExecutadoMaisDeUmaVez() {
        assertDoesNotThrow(fabrica::inicializarSchemaCompleto);
    }

    @Test
    void inicializarSchemaDoNucleoContinuaCriandoSomenteAsTresTabelas() throws Exception {
        FabricaConexao outra = FabricaConexao.paraArquivo(pasta.resolve("nucleo.db"));

        outra.inicializarSchema();

        try (Connection conexao = outra.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' "
                             + "AND name NOT LIKE 'sqlite_%'")) {
            resultado.next();
            assertEquals(3, resultado.getInt(1));
        }
    }

    @Test
    void cartaoComLimiteSemDuasCasasDecimaisEhRejeitado() {
        assertRejeitado(cartao(1, "VISA", "4821", "1000", 1));
    }

    @Test
    void cartaoComBandeiraOuFinalInvalidosEhRejeitado() {
        assertRejeitado(cartao(1, "AMEX", "4821", "5000.00", 1));
        assertRejeitado(cartao(1, "VISA", "48a1", "5000.00", 1));
    }

    @Test
    void cartaoDeContaInexistenteEhRejeitadoPelaChaveEstrangeira() {
        assertRejeitado(cartao(1, "VISA", "4821", "5000.00", 1).replace("VALUES (1, 10,", "VALUES (1, 99,"));
    }

    @Test
    void contaNaoPodeTerDoisCartoesPrincipais() throws Exception {
        executar(CARTAO_VALIDO);

        assertRejeitado(cartao(2, "MASTERCARD", "1187", "3000.00", 1));
    }

    @Test
    void contaPodeTerUmCartaoPrincipalEUmSecundario() {
        assertDoesNotThrow(() -> {
            executar(CARTAO_VALIDO);
            executar(cartao(2, "MASTERCARD", "1187", "3000.00", 0));
        });
    }

    @Test
    void agenciaComMenosDeQuatroDigitosEhRejeitada() {
        assertRejeitado("INSERT INTO dados_conta VALUES (10, '001', '10482', '7')");
    }

    @Test
    void faturaDuplicadaNaMesmaCompetenciaEhRejeitada() throws Exception {
        executar(CARTAO_VALIDO);
        executar("INSERT INTO faturas VALUES (1, 1, '2026-11', '2026-11-10', '2026-11-17', 'ABERTA')");

        assertRejeitado("INSERT INTO faturas VALUES (2, 1, '2026-11', '2026-11-10', '2026-11-17', 'ABERTA')");
    }

    @Test
    void faturaComStatusInvalidoEhRejeitada() throws Exception {
        executar(CARTAO_VALIDO);

        assertRejeitado("INSERT INTO faturas VALUES (1, 1, '2026-11', '2026-11-10', '2026-11-17', 'ATRASADA')");
    }

    @Test
    void lancamentoComParcelaMaiorQueOTotalEhRejeitado() throws Exception {
        executar(CARTAO_VALIDO);
        executar("INSERT INTO faturas VALUES (1, 1, '2026-11', '2026-11-10', '2026-11-17', 'ABERTA')");

        assertRejeitado("INSERT INTO lancamentos_cartao VALUES "
                + "(1, 1, 1, NULL, 'Loja', NULL, '10.00', 3, 2, '2026-11-01')");
    }

    @Test
    void detalheDeTransacaoInexistenteEhRejeitado() {
        assertRejeitado("INSERT INTO transacao_detalhes VALUES (99, NULL, 'Mercado', NULL)");
    }

    @Test
    void chavePixDuplicadaEhRejeitada() throws Exception {
        executar("INSERT INTO chaves_pix VALUES (1, 10, 'EMAIL', 'ana@poobank.com', '2026-11-01 00:00:00.000000')");

        assertRejeitado("INSERT INTO chaves_pix VALUES (2, 10, 'EMAIL', 'ana@poobank.com', '2026-11-01 00:00:00.000000')");
    }

    @Test
    void pagamentoDeFaturaExigeUmaTransacaoExistente() throws Exception {
        executar(CARTAO_VALIDO);
        executar("INSERT INTO faturas VALUES (1, 1, '2026-11', '2026-11-10', '2026-11-17', 'ABERTA')");

        assertRejeitado("INSERT INTO pagamentos_fatura VALUES (1, 1, 77, '10.00', '2026-11-05')");
    }

    @Test
    void metaComValorSemDuasCasasDecimaisEhRejeitada() {
        assertRejeitado("INSERT INTO metas VALUES (1, 10, 'Viagem', '5000', '10.00', NULL)");
    }
}
