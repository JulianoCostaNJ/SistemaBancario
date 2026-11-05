package persistence;

/**
 * Falha na camada de persistência.
 *
 * <p>As implementações dos repositórios convertem erros da infraestrutura (por exemplo, falhas de
 * acesso ao banco) nesta exceção, para que nem o domínio nem a aplicação conheçam detalhes de
 * JDBC. Quando ela é lançada, nenhuma gravação parcial permanece confirmada.</p>
 */
public class PersistenciaException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Cria a exceção com uma mensagem e a causa original.
     *
     * @param mensagem descrição da operação de persistência que falhou
     * @param causa erro original da infraestrutura
     */
    public PersistenciaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }

    /**
     * Cria a exceção apenas com uma mensagem.
     *
     * @param mensagem descrição da falha
     */
    public PersistenciaException(String mensagem) {
        super(mensagem);
    }
}
