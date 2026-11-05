package persistence;

import domain.Conta;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementação de {@link TransacaoRepository} sobre SQLite, acessado por JDBC.
 *
 * <p>Todo o SQL de transações fica nesta classe. Cada operação usa uma conexão própria, aberta por
 * {@link FabricaConexao} e fechada ao final, nunca compartilhada entre threads. Valores monetários
 * são gravados como texto decimal exato, e o horário em formato fixo, com microssegundos, para que
 * a ordem alfabética coincida com a cronológica (frações menores que um microssegundo são
 * descartadas).</p>
 *
 * <p>Os identificadores são gerados em memória, a partir do maior identificador gravado, e por
 * isso deve existir <b>uma única instância</b> deste repositório por banco. Se duas instâncias
 * gerassem o mesmo identificador, o banco recusaria a segunda gravação, nunca a duplicaria.</p>
 */
public final class TransacaoRepositorySqlite implements TransacaoRepository {

    private static final DateTimeFormatter FORMATO_HORARIO =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private static final String SQL_MAIOR_ID = "SELECT COALESCE(MAX(id), 0) FROM transacoes";

    private static final String SQL_GRAVAR_TRANSACAO =
            "INSERT INTO transacoes (id, conta_origem, conta_destino, tipo, valor, horario) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_ATUALIZAR_SALDO = "UPDATE contas SET saldo = ? WHERE numero = ?";

    private static final String SQL_EXTRATO =
            "SELECT id, conta_origem, conta_destino, tipo, valor, horario FROM transacoes "
                    + "WHERE conta_origem = ? OR conta_destino = ? ORDER BY horario, id";

    private final FabricaConexao fabrica;
    private final AtomicLong ultimoId;

    /**
     * Cria o repositório e lê, do banco, o maior identificador de transação já gravado.
     *
     * @param fabrica fábrica das conexões com o banco, cujo esquema já deve estar criado
     * @throws IllegalArgumentException se a fábrica for nula
     * @throws PersistenciaException se o maior identificador não puder ser lido
     */
    public TransacaoRepositorySqlite(FabricaConexao fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException("A fábrica de conexões é obrigatória.");
        }
        this.fabrica = fabrica;
        this.ultimoId = new AtomicLong(lerMaiorId());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Identificadores obtidos e não usados deixam lacunas na sequência, o que é aceitável.</p>
     */
    @Override
    public long proximoId() {
        return ultimoId.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Os saldos são lidos das contas antes de qualquer acesso ao banco, de modo que nenhuma
     * operação JDBC acontece dentro da região crítica de uma conta. Cada conta fornece um saldo
     * válido de um instante, mas dois saldos de contas diferentes não são lidos no mesmo instante:
     * quem chama deve serializar operações que afetem as mesmas contas.</p>
     *
     * @throws IllegalArgumentException se a transação ou a coleção forem nulas, ou se alguma conta
     *         da coleção for nula
     */
    @Override
    public void registrarOperacao(Transacao transacao, Collection<Conta> contasAfetadas) {
        if (transacao == null) {
            throw new IllegalArgumentException("A transação é obrigatória.");
        }
        if (contasAfetadas == null) {
            throw new IllegalArgumentException("As contas afetadas são obrigatórias.");
        }
        List<SaldoDaConta> saldos = new ArrayList<>();
        for (Conta conta : contasAfetadas) {
            if (conta == null) {
                throw new IllegalArgumentException("Uma das contas afetadas é nula.");
            }
            saldos.add(new SaldoDaConta(conta.getNumero(), conta.consultarSaldo().toPlainString()));
        }

        try (Connection conexao = fabrica.criarConexao()) {
            conexao.setAutoCommit(false);
            try {
                gravarTransacao(conexao, transacao);
                for (SaldoDaConta saldo : saldos) {
                    atualizarSaldo(conexao, saldo);
                }
                conexao.commit();
            } catch (SQLException e) {
                reverter(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Não foi possível registrar a transação " + transacao.getId() + ".", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Transacao> consultarExtrato(long numeroConta) {
        try (Connection conexao = fabrica.criarConexao();
             PreparedStatement comando = conexao.prepareStatement(SQL_EXTRATO)) {
            comando.setLong(1, numeroConta);
            comando.setLong(2, numeroConta);
            try (ResultSet resultado = comando.executeQuery()) {
                List<Transacao> extrato = new ArrayList<>();
                while (resultado.next()) {
                    extrato.add(lerTransacao(resultado));
                }
                return extrato;
            }
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Não foi possível consultar o extrato da conta " + numeroConta + ".", e);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new PersistenciaException(
                    "Há uma transação inválida no banco para a conta " + numeroConta + ".", e);
        }
    }

    private long lerMaiorId() {
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(SQL_MAIOR_ID)) {
            resultado.next();
            return resultado.getLong(1);
        } catch (SQLException e) {
            throw new PersistenciaException(
                    "Não foi possível ler o maior identificador de transação.", e);
        }
    }

    private static void gravarTransacao(Connection conexao, Transacao transacao)
            throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_GRAVAR_TRANSACAO)) {
            comando.setLong(1, transacao.getId());
            definirContaOpcional(comando, 2, transacao.getContaOrigem());
            definirContaOpcional(comando, 3, transacao.getContaDestino());
            comando.setString(4, transacao.getTipo().name());
            comando.setString(5, transacao.getValor().toPlainString());
            comando.setString(6, transacao.getHorario().format(FORMATO_HORARIO));
            comando.executeUpdate();
        }
    }

    private static void definirContaOpcional(PreparedStatement comando, int posicao, Long conta)
            throws SQLException {
        if (conta == null) {
            comando.setNull(posicao, Types.INTEGER);
        } else {
            comando.setLong(posicao, conta);
        }
    }

    private static void atualizarSaldo(Connection conexao, SaldoDaConta saldo) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_ATUALIZAR_SALDO)) {
            comando.setString(1, saldo.saldo());
            comando.setLong(2, saldo.numero());
            if (comando.executeUpdate() != 1) {
                throw new SQLException("A conta " + saldo.numero() + " não está gravada.");
            }
        }
    }

    private static Transacao lerTransacao(ResultSet resultado) throws SQLException {
        long origem = resultado.getLong(2);
        Long contaOrigem = resultado.wasNull() ? null : origem;
        long destino = resultado.getLong(3);
        Long contaDestino = resultado.wasNull() ? null : destino;
        return new Transacao(resultado.getLong(1), contaOrigem, contaDestino,
                TipoTransacao.valueOf(resultado.getString(4)),
                new BigDecimal(resultado.getString(5)),
                LocalDateTime.parse(resultado.getString(6), FORMATO_HORARIO));
    }

    private static void reverter(Connection conexao, SQLException causa) {
        try {
            conexao.rollback();
        } catch (SQLException falhaNoRollback) {
            causa.addSuppressed(falhaNoRollback);
        }
    }

    /** Saldo de uma conta já convertido para o texto que será gravado. */
    private record SaldoDaConta(long numero, String saldo) {
    }
}
