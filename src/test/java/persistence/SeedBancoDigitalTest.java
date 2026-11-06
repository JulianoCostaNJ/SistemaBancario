package persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Testes de {@code seed-banco-digital.sql}: os dados gravados são coerentes entre si e com o
 * histórico financeiro. O histórico é montado direto pelos repositórios, com os mesmos horários
 * que {@code app.PopuladorBancoDigital} usa.
 */
class SeedBancoDigitalTest {

    private static final String COMANDO_TOTAL_DE_LINHAS =
            "SELECT (SELECT COUNT(*) FROM categorias) + (SELECT COUNT(*) FROM dados_conta) "
                    + "+ (SELECT COUNT(*) FROM credenciais) + (SELECT COUNT(*) FROM chaves_pix) "
                    + "+ (SELECT COUNT(*) FROM cartoes) + (SELECT COUNT(*) FROM faturas) "
                    + "+ (SELECT COUNT(*) FROM lancamentos_cartao) "
                    + "+ (SELECT COUNT(*) FROM transacao_detalhes) "
                    + "+ (SELECT COUNT(*) FROM pagamentos_fatura) + (SELECT COUNT(*) FROM metas)";

    @TempDir
    Path pasta;

    private FabricaConexao fabrica;

    @BeforeEach
    void prepararBancoComHistorico() {
        fabrica = FabricaConexao.paraArquivo(pasta.resolve("seed.db"));
        fabrica.inicializarSchemaCompleto();
        ContaRepositorySqlite contas = new ContaRepositorySqlite(fabrica);
        TransacaoRepositorySqlite transacoes = new TransacaoRepositorySqlite(fabrica);
        Conta mariana = new Conta(1L, new Cliente(1L, "Mariana Costa", "321.654.987-00"),
                new BigDecimal("4990.00"));
        Conta lucas = new Conta(2L, new Cliente(2L, "Lucas Almeida", "456.789.123-00"),
                new BigDecimal("1500.00"));
        contas.inserir(mariana);
        contas.inserir(lucas);

        sacar(transacoes, mariana, "890.00", "2026-08-11T10:05:00");
        sacar(transacoes, mariana, "1120.00", "2026-09-12T09:40:00");
        sacar(transacoes, mariana, "980.00", "2026-10-14T16:20:00");
        BigDecimal deposito = new BigDecimal("1500.00");
        mariana.depositar(deposito);
        transacoes.registrarOperacao(new Transacao(transacoes.proximoId(), null, mariana.getNumero(),
                TipoTransacao.DEPOSITO, deposito, LocalDateTime.parse("2026-11-03T09:15:00")),
                List.of(mariana));
        BigDecimal transferido = new BigDecimal("350.00");
        mariana.transferirPara(lucas, transferido);
        transacoes.registrarOperacao(new Transacao(transacoes.proximoId(), mariana.getNumero(),
                lucas.getNumero(), TipoTransacao.TRANSFERENCIA, transferido,
                LocalDateTime.parse("2026-11-04T14:30:00")), List.of(mariana, lucas));
        sacar(transacoes, mariana, "200.00", "2026-11-05T10:20:00");

        fabrica.popularDadosBancoDigital();
    }

    private static void sacar(TransacaoRepositorySqlite transacoes, Conta conta, String valor,
                              String horario) {
        BigDecimal quantia = new BigDecimal(valor);
        conta.sacar(quantia);
        transacoes.registrarOperacao(new Transacao(transacoes.proximoId(), conta.getNumero(), null,
                TipoTransacao.SAQUE, quantia, LocalDateTime.parse(horario)), List.of(conta));
    }

    private static List<String> consultar(FabricaConexao banco, String sql) throws SQLException {
        List<String> valores = new ArrayList<>();
        try (Connection conexao = banco.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(sql)) {
            while (resultado.next()) {
                valores.add(resultado.getString(1));
            }
        }
        return valores;
    }

    private BigDecimal somar(String sql) throws SQLException {
        BigDecimal total = BigDecimal.ZERO;
        for (String valor : consultar(fabrica, sql)) {
            total = total.add(new BigDecimal(valor));
        }
        return total;
    }

    private static void assertValor(String esperado, BigDecimal obtido) {
        assertEquals(0, new BigDecimal(esperado).compareTo(obtido),
                "Esperado " + esperado + " mas foi " + obtido);
    }

    @Test
    void contaDeMarianaTemAgenciaENumeroExibidos() throws Exception {
        List<String> exibida = consultar(fabrica,
                "SELECT d.agencia || ' ' || d.numero_exibicao || '-' || d.digito FROM dados_conta d "
                        + "JOIN contas c ON c.numero = d.conta_numero "
                        + "JOIN clientes cl ON cl.id = c.cliente_id "
                        + "WHERE cl.documento = '321.654.987-00'");

        assertEquals(List.of("0001 10482-7"), exibida);
    }

    @Test
    void limiteDisponivelDoCartaoPrincipalEhCalculadoAPartirDosLancamentos() throws Exception {
        BigDecimal limiteTotal = new BigDecimal(
                consultar(fabrica, "SELECT limite_total FROM cartoes WHERE id = 1").get(0));
        BigDecimal utilizado = somar(
                "SELECT l.valor FROM lancamentos_cartao l JOIN faturas f ON f.id = l.fatura_id "
                        + "WHERE l.cartao_id = 1 AND f.status <> 'PAGA'");

        assertValor("5000.00", limiteTotal);
        assertValor("2550.00", utilizado);
        assertValor("2450.00", limiteTotal.subtract(utilizado));
    }

    @Test
    void faturaAbertaSomaOsSeusLancamentos() throws Exception {
        BigDecimal total = somar(
                "SELECT l.valor FROM lancamentos_cartao l JOIN faturas f ON f.id = l.fatura_id "
                        + "WHERE f.status = 'ABERTA'");

        assertValor("1240.00", total);
    }

    @Test
    void gastosDeNovembroSomamECategorizamComoNoPrototipo() throws Exception {
        String base = "SELECT l.valor FROM lancamentos_cartao l "
                + "JOIN categorias cat ON cat.id = l.categoria_id "
                + "WHERE l.data_compra LIKE '2026-11-%'";

        assertValor("550.00", somar(base));
        assertValor("165.00", somar(base + " AND cat.nome = 'Saúde'"));
        assertValor("137.50", somar(base + " AND cat.nome = 'Alimentação'"));
        assertValor("110.00", somar(base + " AND cat.nome = 'Lazer'"));
        assertValor("137.50", somar(base + " AND cat.nome = 'Transporte'"));
    }

    @Test
    void cadaFaturaPagaConfereComSeuPagamentoEComATransacao() throws Exception {
        List<String> pagas = consultar(fabrica, "SELECT id FROM faturas WHERE status = 'PAGA' ORDER BY id");

        assertEquals(3, pagas.size());
        for (String id : pagas) {
            BigDecimal lancamentos = somar(
                    "SELECT valor FROM lancamentos_cartao WHERE fatura_id = " + id);
            BigDecimal pagamento = somar(
                    "SELECT valor FROM pagamentos_fatura WHERE fatura_id = " + id);
            BigDecimal transacao = somar(
                    "SELECT t.valor FROM pagamentos_fatura p JOIN transacoes t ON t.id = p.transacao_id "
                            + "WHERE p.fatura_id = " + id);
            assertEquals(0, lancamentos.compareTo(pagamento), "Fatura " + id + ": lançamentos x pagamento");
            assertEquals(0, pagamento.compareTo(transacao), "Fatura " + id + ": pagamento x transação");
        }
    }

    @Test
    void todaTransacaoDoHistoricoTemDetalhe() throws Exception {
        assertEquals("6", consultar(fabrica, "SELECT COUNT(*) FROM transacao_detalhes").get(0));
    }

    @Test
    void senhaDeDemonstracaoConfereComOHashGravado() throws Exception {
        String[] partes = consultar(fabrica,
                "SELECT senha_hash || ',' || salt || ',' || iteracoes FROM credenciais "
                        + "WHERE conta_numero = 1").get(0).split(",");

        assertEquals(partes[0], derivar("poobank@2026", partes[1], Integer.parseInt(partes[2])));
    }

    @Test
    void popularDeNovoNaoDuplicaNenhumaLinha() throws Exception {
        String antes = consultar(fabrica, COMANDO_TOTAL_DE_LINHAS).get(0);

        fabrica.popularDadosBancoDigital();

        assertEquals(antes, consultar(fabrica, COMANDO_TOTAL_DE_LINHAS).get(0));
    }

    @Test
    void popularSemAsContasFalhaENaoGravaNada() throws Exception {
        FabricaConexao vazia = FabricaConexao.paraArquivo(pasta.resolve("vazio.db"));
        vazia.inicializarSchemaCompleto();

        assertThrows(PersistenciaException.class, vazia::popularDadosBancoDigital);

        assertEquals("0", consultar(vazia, "SELECT COUNT(*) FROM categorias").get(0));
    }

    private static String derivar(String senha, String saltHex, int iteracoes)
            throws GeneralSecurityException {
        PBEKeySpec especificacao = new PBEKeySpec(senha.toCharArray(),
                HexFormat.of().parseHex(saltHex), iteracoes, 256);
        byte[] chave = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(especificacao).getEncoded();
        return HexFormat.of().formatHex(chave);
    }
}
