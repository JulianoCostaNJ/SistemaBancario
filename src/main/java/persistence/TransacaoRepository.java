package persistence;

import domain.Conta;
import domain.Transacao;
import java.util.Collection;
import java.util.List;

/**
 * Contrato de persistência das transações e do extrato por conta.
 *
 * <p>Este contrato expressa apenas operações de negócio: não conhece SQL, conexões nem arquivos.
 * As transações são imutáveis e, uma vez registradas, não são alteradas.</p>
 */
public interface TransacaoRepository {

    /**
     * Fornece o próximo identificador de transação, único mesmo quando chamado por várias
     * threads e continuando a sequência já gravada em execuções anteriores.
     *
     * @return identificador ainda não utilizado
     * @throws PersistenciaException se não for possível determinar o próximo identificador
     */
    long proximoId();

    /**
     * Registra uma transação concluída e grava, na mesma operação atômica, os saldos atuais das
     * contas afetadas. Ou tudo é confirmado, ou nada é: uma transferência nunca fica gravada pela
     * metade. As contas afetadas devem ter sido salvas antes em {@link ContaRepository}.
     *
     * @param transacao transação concluída a registrar
     * @param contasAfetadas contas cujos saldos mudaram por causa da transação, com o saldo atual
     * @throws PersistenciaException se a gravação falhar; nada é confirmado nesse caso
     */
    void registrarOperacao(Transacao transacao, Collection<Conta> contasAfetadas);

    /**
     * Consulta o extrato de uma conta: as transações em que ela aparece como origem ou como
     * destino, em ordem cronológica (e, em caso de mesmo horário, pelo identificador). Para saber
     * se uma transferência foi entrada ou saída, compare o número da conta com a origem e o
     * destino da transação.
     *
     * @param numeroConta número da conta
     * @return transações da conta, possivelmente vazia
     * @throws PersistenciaException se a consulta falhar
     */
    List<Transacao> consultarExtrato(long numeroConta);
}
