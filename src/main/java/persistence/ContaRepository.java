package persistence;

import java.util.List;
import java.util.Optional;

import domain.Conta;

/**
 * Contrato de persistência das contas e de seus titulares.
 *
 * <p>Este contrato expressa apenas operações de negócio: não conhece SQL, conexões nem arquivos.
 * Os saldos devem ser guardados e recuperados sem perda de precisão decimal. Uma conta é criada
 * uma única vez; depois disso, o saldo só muda por
 * {@link TransacaoRepository#registrarOperacao(domain.Transacao, java.util.Collection)}.</p>
 */
public interface ContaRepository {

    /**
     * Insere uma conta nova e, se ainda não existir, o seu titular. A gravação é atômica.
     *
     * <p>Esta operação nunca altera uma conta existente: um número de conta repetido é recusado.
     * Um titular que já existe só é reaproveitado se o identificador e o documento forem
     * idênticos aos gravados; o mesmo identificador com dados diferentes, ou o mesmo documento
     * com outro identificador, é recusado.</p>
     *
     * @param conta conta nova, com o saldo inicial
     * @throws IllegalArgumentException se a conta for nula
     * @throws PersistenciaException se o número da conta ou o titular entrarem em conflito com o
     *         que já está gravado, ou se a gravação falhar; nada é confirmado nesse caso
     */
    void inserir(Conta conta);

    /**
     * Fornece o próximo número de conta, único mesmo quando chamado por várias threads e
     * continuando a sequência já gravada em execuções anteriores. Números obtidos e não usados
     * deixam lacunas, o que é aceitável.
     *
     * @return número de conta ainda não utilizado
     */
    long proximoNumeroConta();

    /**
     * Fornece o próximo identificador de cliente, único mesmo quando chamado por várias threads e
     * continuando a sequência já gravada em execuções anteriores.
     *
     * @return identificador de cliente ainda não utilizado
     */
    long proximoIdCliente();

    /**
     * Busca uma conta pelo número, com o titular e o saldo gravados.
     *
     * @param numero número da conta
     * @return a conta, ou vazio se não existir
     * @throws PersistenciaException se a consulta falhar
     */
    Optional<Conta> buscarPorNumero(long numero);

    /**
     * Lista todas as contas gravadas, em ordem crescente de número. Permite recuperar o estado
     * persistido entre execuções do sistema.
     *
     * @return contas gravadas, possivelmente vazia
     * @throws PersistenciaException se a consulta falhar
     */
    List<Conta> listarTodas();
}