package domain;

import java.math.BigDecimal;

/**
 * Raiz de agregado que controla o saldo e preserva suas invariantes.
 *
 * <p>Etapa Green mínima: apenas a validação do saldo inicial. Os métodos de
 * negócio entram nos próximos ciclos TDD. Nenhuma sincronização nesta fase,
 * de propósito (Semana 1: domínio sequencial).</p>
 */
public final class Conta {

    private final long numero;
    private final Cliente titular;
    private BigDecimal saldo;

    /**
     * Cria uma conta com saldo inicial.
     *
     * @param numero número único e imutável da conta
     * @param titular titular da conta
     * @param saldoInicial saldo inicial, que não pode ser negativo
     * @throws IllegalArgumentException se o saldo inicial for negativo
     */
    public Conta(long numero, Cliente titular, BigDecimal saldoInicial) {
        if (saldoInicial.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
        }
        this.numero = numero;
        this.titular = titular;
        this.saldo = saldoInicial;
    }

    /**
     * Retorna o número da conta.
     *
     * @return número da conta
     */
    public long getNumero() {
        return numero;
    }

    /**
     * Retorna o titular da conta.
     *
     * @return titular
     */
    public Cliente getTitular() {
        return titular;
    }
}
