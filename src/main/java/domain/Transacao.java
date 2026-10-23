package domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * Registro imutável de uma operação bancária efetivamente concluída.
 *
 * <p>A combinação de contas depende do tipo: {@code DEPOSITO} possui apenas conta de destino,
 * {@code SAQUE} possui apenas conta de origem e {@code TRANSFERENCIA} possui as duas, que
 * precisam ser diferentes. O horário é recebido na criação para permitir testes determinísticos.</p>
 */
public final class Transacao {

    private static final int ESCALA = 2;

    private final long id;
    private final Long contaOrigem;
    private final Long contaDestino;
    private final TipoTransacao tipo;
    private final BigDecimal valor;
    private final LocalDateTime horario;

    /**
     * Cria uma transação.
     *
     * @param id identificador da transação
     * @param contaOrigem número da conta de origem, ou {@code null} quando o tipo não a possui
     * @param contaDestino número da conta de destino, ou {@code null} quando o tipo não a possui
     * @param tipo classificação da operação
     * @param valor valor positivo, com no máximo duas casas decimais
     * @param horario momento em que a operação foi concluída
     * @throws IllegalArgumentException se algum campo obrigatório for nulo, se o valor for
     *         inválido ou se a combinação de contas não corresponder ao tipo
     */
    public Transacao(long id, Long contaOrigem, Long contaDestino, TipoTransacao tipo,
                     BigDecimal valor, LocalDateTime horario) {
        if (tipo == null) {
            throw new IllegalArgumentException("O tipo da transação é obrigatório.");
        }
        if (horario == null) {
            throw new IllegalArgumentException("O horário da transação é obrigatório.");
        }
        BigDecimal valorNormalizado = validarValor(valor);
        validarContas(tipo, contaOrigem, contaDestino);

        this.id = id;
        this.contaOrigem = contaOrigem;
        this.contaDestino = contaDestino;
        this.tipo = tipo;
        this.valor = valorNormalizado;
        this.horario = horario;
    }

    private static BigDecimal validarValor(BigDecimal valor) {
        if (valor == null) {
            throw new IllegalArgumentException("O valor da transação é obrigatório.");
        }
        BigDecimal normalizado;
        try {
            normalizado = valor.setScale(ESCALA, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "O valor da transação deve ter no máximo duas casas decimais.", e);
        }
        if (normalizado.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("O valor da transação deve ser maior que zero.");
        }
        return normalizado;
    }

    private static void validarContas(TipoTransacao tipo, Long contaOrigem, Long contaDestino) {
        switch (tipo) {
            case DEPOSITO -> {
                if (contaOrigem != null || contaDestino == null) {
                    throw new IllegalArgumentException(
                            "O depósito exige apenas a conta de destino.");
                }
            }
            case SAQUE -> {
                if (contaOrigem == null || contaDestino != null) {
                    throw new IllegalArgumentException(
                            "O saque exige apenas a conta de origem.");
                }
            }
            case TRANSFERENCIA -> {
                if (contaOrigem == null || contaDestino == null) {
                    throw new IllegalArgumentException(
                            "A transferência exige a conta de origem e a conta de destino.");
                }
                if (contaOrigem.equals(contaDestino)) {
                    throw new IllegalArgumentException(
                            "A conta de origem deve ser diferente da conta de destino.");
                }
            }
        }
    }

    /**
     * Retorna o identificador da transação.
     *
     * @return identificador
     */
    public long getId() {
        return id;
    }

    /**
     * Retorna o número da conta de origem.
     *
     * @return número da conta de origem, ou {@code null} em depósitos
     */
    public Long getContaOrigem() {
        return contaOrigem;
    }

    /**
     * Retorna o número da conta de destino.
     *
     * @return número da conta de destino, ou {@code null} em saques
     */
    public Long getContaDestino() {
        return contaDestino;
    }

    /**
     * Retorna o tipo da transação.
     *
     * @return tipo
     */
    public TipoTransacao getTipo() {
        return tipo;
    }

    /**
     * Retorna o valor da transação, com duas casas decimais.
     *
     * @return valor positivo
     */
    public BigDecimal getValor() {
        return valor;
    }

    /**
     * Retorna o horário em que a operação foi concluída.
     *
     * @return horário
     */
    public LocalDateTime getHorario() {
        return horario;
    }
}
