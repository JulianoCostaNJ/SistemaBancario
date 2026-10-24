package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Raiz de agregado que controla o saldo e preserva suas invariantes.
 *
 * <p>O saldo é privado e só é alterado pelas operações desta classe. Valores monetários usam
 * {@link BigDecimal} com duas casas decimais: valores com mais casas são rejeitados, nunca
 * arredondados em silêncio. Uma operação rejeitada não altera nenhum saldo.</p>
 *
 * <p>{@link #consultarSaldo()}, {@link #depositar(BigDecimal)} e {@link #sacar(BigDecimal)} são
 * {@code synchronized} e usam o monitor da própria conta: a validação do saldo e a sua alteração
 * ocorrem na mesma região crítica. {@link #transferirPara(Conta, BigDecimal)} bloqueia as duas
 * contas sempre em ordem crescente de número, o que impede deadlock entre transferências em
 * sentidos opostos.</p>
 */
public final class Conta {

    private static final int ESCALA = 2;

    private final long numero;
    private final Cliente titular;
    private BigDecimal saldo;

    /**
     * Cria uma conta com saldo inicial.
     *
     * @param numero número único e imutável da conta
     * @param titular titular da conta
     * @param saldoInicial saldo inicial, não negativo e com no máximo duas casas decimais
     * @throws IllegalArgumentException se o titular ou o saldo forem nulos, se o saldo for
     *         negativo ou se tiver mais de duas casas decimais
     */
    public Conta(long numero, Cliente titular, BigDecimal saldoInicial) {
        if (titular == null) {
            throw new IllegalArgumentException("O titular da conta é obrigatório.");
        }
        if (saldoInicial == null) {
            throw new IllegalArgumentException("O saldo inicial é obrigatório.");
        }
        BigDecimal normalizado = normalizarEscala(saldoInicial, "O saldo inicial");
        if (normalizado.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("O saldo inicial não pode ser negativo.");
        }
        this.numero = numero;
        this.titular = titular;
        this.saldo = normalizado;
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

    /**
     * Consulta o saldo atual.
     *
     * @return saldo com duas casas decimais
     */
    public synchronized BigDecimal consultarSaldo() {
        return saldo;
    }

    /**
     * Deposita um valor na conta.
     *
     * @param valor valor positivo, com no máximo duas casas decimais
     * @throws IllegalArgumentException se o valor for nulo, zero, negativo ou tiver mais de
     *         duas casas decimais
     */
    public synchronized void depositar(BigDecimal valor) {
        BigDecimal valorValido = validarValorDaOperacao(valor);
        saldo = saldo.add(valorValido);
    }

    /**
     * Saca um valor da conta.
     *
     * @param valor valor positivo, com no máximo duas casas decimais
     * @throws IllegalArgumentException se o valor for nulo, zero, negativo ou tiver mais de
     *         duas casas decimais
     * @throws SaldoInsuficienteException se o saldo for menor que o valor; o saldo não é alterado
     */
    public synchronized void sacar(BigDecimal valor) {
        BigDecimal valorValido = validarValorDaOperacao(valor);
        exigirSaldoSuficiente(valorValido);
        saldo = saldo.subtract(valorValido);
    }

    /**
     * Transfere um valor desta conta para a conta de destino.
     *
     * <p>Os monitores das duas contas são adquiridos sempre na mesma ordem global: primeiro o da
     * conta de menor número e depois o da de maior número. Com os dois bloqueios, o saldo da origem
     * é verificado e os dois saldos são alterados de forma indivisível para as demais operações.
     * Uma transferência recusada não modifica nenhuma das contas. O método não é declarado
     * {@code synchronized} de propósito: isso bloquearia a origem antes da ordenação.</p>
     *
     * @param destino conta que receberá o valor, diferente desta conta
     * @param valor valor positivo, com no máximo duas casas decimais
     * @throws IllegalArgumentException se o destino for nulo ou for esta mesma conta, ou se o
     *         valor for nulo, zero, negativo ou tiver mais de duas casas decimais
     * @throws SaldoInsuficienteException se o saldo desta conta for menor que o valor
     */
    public void transferirPara(Conta destino, BigDecimal valor) {
        if (destino == null) {
            throw new IllegalArgumentException("A conta de destino é obrigatória.");
        }
        if (destino.numero == this.numero) {
            throw new IllegalArgumentException(
                    "Não é possível transferir para a mesma conta.");
        }
        BigDecimal valorValido = validarValorDaOperacao(valor);

        Conta menor = this.numero < destino.numero ? this : destino;
        Conta maior = menor == this ? destino : this;
        synchronized (menor) {
            synchronized (maior) {
                exigirSaldoSuficiente(valorValido);
                this.saldo = this.saldo.subtract(valorValido);
                destino.saldo = destino.saldo.add(valorValido);
            }
        }
    }

    private static BigDecimal validarValorDaOperacao(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("O valor da operação é obrigatório.");
        }
        BigDecimal normalizado = normalizarEscala(valor, "O valor da operação");
        if (normalizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da operação deve ser maior que zero.");
        }
        return normalizado;
    }

    private static BigDecimal normalizarEscala(BigDecimal valor, String descricao) {
        try {
            return valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    descricao + " deve ter no máximo duas casas decimais.", e);
        }
    }

    private void exigirSaldoSuficiente(BigDecimal valor) {
        if (saldo.compareTo(valor) < 0) {
            throw new SaldoInsuficienteException("Saldo insuficiente para realizar a operação.");
        }
    }
}