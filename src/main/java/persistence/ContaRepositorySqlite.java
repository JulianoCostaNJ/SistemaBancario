package persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import domain.Cliente;
import domain.Conta;

/**
 * Implementação de {@link ContaRepository} sobre SQLite, acessado por JDBC.
 *
 * <p>Todo o SQL de contas e titulares fica nesta classe. Cada operação usa uma conexão própria,
 * aberta por {@link FabricaConexao} e fechada ao final, nunca compartilhada entre threads. O saldo
 * é gravado como texto, com a representação decimal exata do {@link BigDecimal}.</p>
 *
 * <p>O saldo é lido da {@link Conta} antes de qualquer acesso ao banco, de modo que nenhuma
 * operação JDBC acontece dentro da região crítica da conta.</p>
 *
 * <p>Os números de conta e os identificadores de cliente são gerados em memória, a partir dos
 * maiores valores gravados, e por isso deve existir <b>uma única instância</b> deste repositório
 * por banco. Se duas instâncias gerassem o mesmo valor, o banco recusaria a segunda gravação,
 * nunca a duplicaria.</p>
 */
public final class ContaRepositorySqlite implements ContaRepository {

    private static final String SQL_MAIOR_NUMERO = "SELECT COALESCE(MAX(numero), 0) FROM contas";

    private static final String SQL_MAIOR_ID_CLIENTE = "SELECT COALESCE(MAX(id), 0) FROM clientes";

    private static final String SQL_INSERIR_CLIENTE =
            "INSERT INTO clientes (id, nome, documento) VALUES (?, ?, ?) "
                    + "ON CONFLICT(id) DO NOTHING";

    private static final String SQL_BUSCAR_CLIENTE =
            "SELECT nome, documento FROM clientes WHERE id = ?";

    private static final String SQL_INSERIR_CONTA =
            "INSERT INTO contas (numero, cliente_id, saldo) VALUES (?, ?, ?)";

    private static final String SQL_SELECIONAR_CONTAS =
            "SELECT c.numero, c.saldo, cl.id, cl.nome, cl.documento "
                    + "FROM contas c JOIN clientes cl ON cl.id = c.cliente_id";

    private final FabricaConexao fabrica;
    private final AtomicLong ultimoNumero;
    private final AtomicLong ultimoIdCliente;

    /**
     * Cria o repositório e lê, do banco, o maior número de conta e o maior identificador de
     * cliente já gravados.
     *
     * @param fabrica fábrica das conexões com o banco, cujo esquema já deve estar criado
     * @throws IllegalArgumentException se a fábrica for nula
     * @throws PersistenciaException se os maiores valores não puderem ser lidos
     */
    public ContaRepositorySqlite(FabricaConexao fabrica) {
        if (fabrica == null) {
            throw new IllegalArgumentException("A fábrica de conexões é obrigatória.");
        }
        this.fabrica = fabrica;
        this.ultimoNumero = new AtomicLong(lerMaior(SQL_MAIOR_NUMERO));
        this.ultimoIdCliente = new AtomicLong(lerMaior(SQL_MAIOR_ID_CLIENTE));
    }

    /** {@inheritDoc} */
    @Override
    public long proximoNumeroConta() {
        return ultimoNumero.incrementAndGet();
    }

    /** {@inheritDoc} */
    @Override
    public long proximoIdCliente() {
        return ultimoIdCliente.incrementAndGet();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Depois de gravar, o gerador de números e o de identificadores avançam, se preciso, para
     * nunca devolverem um valor que já foi usado por uma inserção manual.</p>
     */
    @Override
    public void inserir(Conta conta) {
        if (conta == null) {
            throw new IllegalArgumentException("A conta é obrigatória.");
        }
        Cliente titular = conta.getTitular();
        long numero = conta.getNumero();
        String saldo = conta.consultarSaldo().toPlainString();

        try (Connection conexao = fabrica.criarConexao()) {
            conexao.setAutoCommit(false);
            try {
                garantirTitular(conexao, titular);
                inserirConta(conexao, numero, titular.getId(), saldo);
                conexao.commit();
            } catch (SQLException e) {
                reverter(conexao, e);
                throw e;
            }
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível inserir a conta " + numero + ".", e);
        }
        ultimoNumero.accumulateAndGet(numero, Math::max);
        ultimoIdCliente.accumulateAndGet(titular.getId(), Math::max);
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

    private long lerMaior(String sql) {
        try (Connection conexao = fabrica.criarConexao();
             Statement comando = conexao.createStatement();
             ResultSet resultado = comando.executeQuery(sql)) {
            resultado.next();
            return resultado.getLong(1);
        } catch (SQLException e) {
            throw new PersistenciaException("Não foi possível ler o maior valor gravado.", e);
        }
    }

    private static void garantirTitular(Connection conexao, Cliente titular) throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR_CLIENTE)) {
            comando.setLong(1, titular.getId());
            comando.setString(2, titular.getNome());
            comando.setString(3, titular.getDocumento());
            comando.executeUpdate();
        }
        try (PreparedStatement comando = conexao.prepareStatement(SQL_BUSCAR_CLIENTE)) {
            comando.setLong(1, titular.getId());
            try (ResultSet gravado = comando.executeQuery()) {
                boolean existe = gravado.next();
                if (!existe || !titular.getNome().equals(gravado.getString(1))
                        || !titular.getDocumento().equals(gravado.getString(2))) {
                    throw new SQLException(
                            "O cliente " + titular.getId() + " já existe com outros dados.");
                }
            }
        }
    }

    private static void inserirConta(Connection conexao, long numero, long clienteId, String saldo)
            throws SQLException {
        try (PreparedStatement comando = conexao.prepareStatement(SQL_INSERIR_CONTA)) {
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