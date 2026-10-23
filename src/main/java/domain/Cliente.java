package domain;

/**
 * Titular de uma ou mais contas.
 *
 * <p>Entidade imutável: nome e documento são obrigatórios e validados na criação.</p>
 */
public final class Cliente {

    private final long id;
    private final String nome;
    private final String documento;

    /**
     * Cria um cliente.
     *
     * @param id identificador do cliente
     * @param nome nome do titular
     * @param documento documento do titular
     * @throws IllegalArgumentException se o nome ou o documento forem nulos ou em branco
     */
    public Cliente(long id, String nome, String documento) {
        exigirTextoPreenchido(nome, "O nome do cliente é obrigatório.");
        exigirTextoPreenchido(documento, "O documento do cliente é obrigatório.");
        this.id = id;
        this.nome = nome;
        this.documento = documento;
    }

    private static void exigirTextoPreenchido(String texto, String mensagem) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    /**
     * Retorna o identificador do cliente.
     *
     * @return identificador
     */
    public long getId() {
        return id;
    }

    /**
     * Retorna o nome do titular.
     *
     * @return nome
     */
    public String getNome() {
        return nome;
    }

    /**
     * Retorna o documento do titular.
     *
     * @return documento
     */
    public String getDocumento() {
        return documento;
    }
}