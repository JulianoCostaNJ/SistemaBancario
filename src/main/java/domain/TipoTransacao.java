package domain;

/**
 * Classificação de uma operação bancária concluída.
 */
public enum TipoTransacao {

    /** Entrada de dinheiro em uma conta. */
    DEPOSITO,

    /** Saída de dinheiro de uma conta. */
    SAQUE,

    /** Movimentação de dinheiro de uma conta de origem para uma conta de destino. */
    TRANSFERENCIA
}
