package persistence;

import domain.Cliente;
import domain.Conta;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementação de {@link ContaRepository} sobre SQLite, acessado por JDBC.
 *
 * <p>Todo o SQL de contas e titulares fica nesta classe. Cada operação usa uma conexão própria,
 * aberta por {@link FabricaConexao} e fechada ao final, nunca compartilhada entre threads. O saldo
 * é gravado como texto, com a representação decimal exata do {@link BigDecimal}.</p>
 *
 * <p>O saldo é lido da {@link Conta} antes de qualquer acesso ao banco, de modo que nenhuma
 * operação JDBC acontece dentro da região crítica da conta.</p>
 */
public final class ContaRepositorySqlite implements ContaRepository {

    private static final String SQL_GRAVAR_CLIENTE =
            "INSERT INTO clientes (id, nome, documento) VALUES (?, ?, ?) "
                    + "ON CONFLICT(id) DO UPDATE SET nome = excluded.nome, "
                    + "documento = excluded.documento";

    private static final String SQL_GRAVAR_CONTA =
            "INSERT INTO contas (numero, cliente_id, saldo) VALUES (?, ?, ?) "
                    + "ON CONFLICT(numero) DO UPDATE SET cliente_id = excluded.cliente_id, "
                    + "saldo = excluded.saldo";

    private static final String SQL_SELECIONAR_CONTAS =
            "SELECT c.numero, c.saldo, cl.id, cl.nome, cl.documento "
                    + "FROM contas c JOIN clientes cl ON cl.id = c.cliente_id";

    private final FabricaConexao fabrica;

    /**
     * Cria o repositório.
     *
     * @param fabrica fábrica das conexões com o banco
     * @throws IllegalArgumentException se a fábrica for nula
     */
    public ContaRepositorySqlite(FabricaConexao fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException("A fábrica de conexões é obrigatória.");
        }
        this.fabrica = fabrica;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException se a conta for nula
     */
    @Override
    public void salvar(Conta conta) {
        if (conta == null) {
            throw new IllegalArgumentException("A conta é obrigatória.");
        }
        Cliente titular = conta.getTitular();
        long numero = conta.getNumero();
        String saldo = conta.consultarSaldo().toPlainString();

        try (Connection conexao = fabrica.criarConexao()) {
            conexao.setAutoCommit(false);
            try {
                gravarTitular(conexao, titular);
                gravarConta(conexao, numero, titular.getId(), saldo);
                conexao.commit();
            } catch (SQLException e) {
                reverter(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível salvar a conta " + numero + ".", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Conta> buscarPorNumero(long numero) {
        String sql = SQL_SELECIONAR_CONTAS + " WHERE c.numero = ?";
        try (Connection conexao = fabrica.criarConexao();
             PreparedStatement comando = conexao.prepareStatement(sql)) {
            comando.setLong(1, numero);
            try (ResultSet resultado = comando.executeQuery()) {
                return resultado.next() ? Optional.of(lerConta(resultado)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível consultar a conta " + numero + ".", e);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("A conta " + numero + " está inválida no banco.", e);
        }
    }

    /** {@inheritDoc} */
    @Override
    public List<Conta> listarTodas() {
        String sql = SQL_SELECIONAR_CONTAS + " ORDER BY c.numero";
        try (Connection conexao = fabrica.criarConexao();
             PreparedStatement comando = conexao.prepareStatement(sql);
             ResultSet resultado = comando.executeQuery()) {
            List<Conta> contas = new ArrayList<>();
            while (resultado.next()) {
                contas.add(lerConta(resultado));
            }
            return contas;
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível listar as contas.", e);
        } catch (IllegalArgumentException e) {
            throw new PersistenciaException("Há uma conta inválida no banco.", e);
        }
    }

    private static void gravarTitular(Connection conexao, Cliente titular) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_GRAVAR_CLIENTE)) {
            comando.setLong(1, titular.getId());
            comando.setString(2, titular.getNome());
            comando.setString(3, titular.getDocumento());
            comando.executeUpdate();
        }
    }

    private static void gravarConta(Connection conexao, long numero, long clienteId, String saldo)
            throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_GRAVAR_CONTA)) {
            comando.setLong(1, numero);
            comando.setLong(2, clienteId);
            comando.setString(3, saldo);
            comando.executeUpdate();
        }
    }

    private static Conta lerConta(ResultSet resultado) throws SQLException {
        Cliente titular = new Cliente(resultado.getLong(3), resultado.getString(4),
                resultado.getString(5));
        return new Conta(resultado.getLong(1), titular, new BigDecimal(resultado.getString(2)));
    }

    private static void reverter(Connection conexao, SQLException causa) {
        try {
            conexao.rollback();
        } catch (SQLException falhaNoRollback) {
            causa.addSuppressed(falhaNoRollback);
        }
    }
}
