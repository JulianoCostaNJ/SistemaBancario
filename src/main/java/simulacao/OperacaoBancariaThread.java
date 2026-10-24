package simulacao;

import domain.Conta;
import domain.SaldoInsuficienteException;
import domain.TipoTransacao;
import java.math.BigDecimal;

/**
 * Tarefa que executa uma operação bancária previamente definida sobre contas do domínio.
 *
 * <p>Esta classe apenas dispara as operações de {@link Conta}; as regras de saldo e a proteção
 * contra acesso concorrente pertencem ao domínio e não são repetidas aqui. Uma operação recusada
 * por saldo insuficiente é registrada como rejeitada, para que o resultado esperado de uma
 * simulação possa ser calculado a partir das operações efetivamente concluídas.</p>
 */
public final class OperacaoBancariaThread implements Runnable {

    private final TipoTransacao tipo;
    private final Conta contaOrigem;
    private final Conta contaDestino;
    private final BigDecimal valor;
    private volatile boolean concluida;
    private volatile boolean rejeitada;

    /**
     * Define a operação a ser executada.
     *
     * @param tipo tipo da operação
     * @param contaOrigem conta debitada (saque e transferência); ignorada no depósito
     * @param contaDestino conta creditada (depósito e transferência); ignorada no saque
     * @param valor valor da operação
     * @throws IllegalArgumentException se o tipo ou o valor forem nulos, ou se faltar uma conta
     *         exigida pelo tipo
     */
    public OperacaoBancariaThread(TipoTransacao tipo, Conta contaOrigem, Conta contaDestino,
                                  BigDecimal valor) {
        if (tipo == null) {
            throw new IllegalArgumentException("O tipo da operação é obrigatório.");
        }
        if (valor == null) {
            throw new IllegalArgumentException("O valor da operação é obrigatório.");
        }
        boolean exigeOrigem = tipo != TipoTransacao.DEPOSITO;
        boolean exigeDestino = tipo != TipoTransacao.SAQUE;
        if ((exigeOrigem && contaOrigem == null) || (exigeDestino && contaDestino == null)) {
            throw new IllegalArgumentException(
                    "Faltam as contas exigidas pelo tipo de operação " + tipo + ".");
        }
        this.tipo = tipo;
        this.contaOrigem = contaOrigem;
        this.contaDestino = contaDestino;
        this.valor = valor;
    }

    /**
     * Executa a operação. Se o saldo for insuficiente, a operação é marcada como rejeitada e
     * nenhuma conta é alterada. Qualquer outra falha é propagada ao executor.
     */
    @Override
    public void run() {
        try {
            switch (tipo) {
                case DEPOSITO -> contaDestino.depositar(valor);
                case SAQUE -> contaOrigem.sacar(valor);
                case TRANSFERENCIA -> contaOrigem.transferirPara(contaDestino, valor);
            }
            concluida = true;
        } catch (SaldoInsuficienteException e) {
            rejeitada = true;
        }
    }

    /**
     * Informa se a operação foi executada com sucesso.
     *
     * @return {@code true} se a operação foi concluída
     */
    public boolean foiConcluida() {
        return concluida;
    }

    /**
     * Informa se a operação foi recusada por saldo insuficiente.
     *
     * @return {@code true} se a operação foi rejeitada
     */
    public boolean foiRejeitada() {
        return rejeitada;
    }
}
