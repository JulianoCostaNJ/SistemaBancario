package domain;

/**
 * Exceção de negócio lançada quando um saque ou uma transferência excede o saldo disponível.
 *
 * <p>Quando esta exceção é lançada, nenhum saldo foi alterado.</p>
 */
public class SaldoInsuficienteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a exceção com a mensagem informada.
     *
     * @param mensagem descrição do motivo da recusa
     */
    public SaldoInsuficienteException(String mensagem) {
        super(mensagem);
    }
}
