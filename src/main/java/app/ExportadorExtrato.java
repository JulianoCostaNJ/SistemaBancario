package app;

import domain.TipoTransacao;
import domain.Transacao;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Exporta o extrato de uma conta para um arquivo de texto em UTF-8, sem BOM.
 *
 * <p>Esta classe só formata e grava: recebe os dados prontos e não conhece o serviço nem o banco.
 * Valores monetários são formatados aqui, a partir do {@link BigDecimal}, no padrão brasileiro
 * ({@code 1.234,56}), sem conversão para ponto flutuante. O arquivo existente é sobrescrito, e a
 * pasta de destino não é criada.</p>
 *
 * <p>O saldo e o extrato são recebidos separadamente: quem chama deve obtê-los sem que outra
 * operação ocorra entre as duas consultas, se precisar que o arquivo seja consistente.</p>
 */
public final class ExportadorExtrato {

    private static final String AGENCIA = "0001";
    private static final int LARGURA = 70;
    private static final DateTimeFormatter FORMATO_DATA_HORA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String FORMATO_LINHA = " %-19s  %-14s  %-10s  %16s";

    private final Clock relogio;

    /**
     * Cria o exportador.
     *
     * @param relogio relógio que fornece o instante de emissão do extrato
     * @throws IllegalArgumentException se o relógio for nulo
     */
    public ExportadorExtrato(Clock relogio) {
        if (relogio == null) {
            throw new IllegalArgumentException("O relógio é obrigatório.");
        }
        this.relogio = relogio;
    }

    /**
     * Grava o extrato de uma conta em um arquivo de texto UTF-8, sobrescrevendo-o se existir.
     * Nada é gravado se algum argumento for inválido.
     *
     * @param numeroConta número da conta do extrato
     * @param saldoAtual saldo atual da conta, com no máximo duas casas decimais
     * @param transacoes movimentos da conta, que são gravados na ordem recebida
     * @param arquivo caminho do arquivo de destino, em uma pasta que já exista
     * @throws IllegalArgumentException se algum argumento for nulo, se o saldo tiver mais de duas
     *         casas decimais ou se alguma transação não pertencer à conta
     * @throws UncheckedIOException se o arquivo não puder ser gravado
     */
    public void exportar(long numeroConta, BigDecimal saldoAtual, List<Transacao> transacoes,
                         Path arquivo) {
        if (saldoAtual == null || transacoes == null || arquivo == null) {
            throw new IllegalArgumentException(
                    "O saldo, as transações e o arquivo de destino são obrigatórios.");
        }
        String conteudo = montarConteudo(numeroConta, saldoAtual, transacoes);
        try (BufferedWriter escritor = Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8)) {
            escritor.write(conteudo);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível gravar o extrato em " + arquivo + ".", e);
        }
    }

    private String montarConteudo(long numeroConta, BigDecimal saldoAtual,
                                  List<Transacao> transacoes) {
        String quebra = System.lineSeparator();
        StringBuilder texto = new StringBuilder();
        adicionar(texto, "=".repeat(LARGURA), quebra);
        adicionar(texto, " POO BANK - EXTRATO DE CONTA", quebra);
        adicionar(texto, "=".repeat(LARGURA), quebra);
        adicionar(texto, " Agência ..........: " + AGENCIA, quebra);
        adicionar(texto, " Conta ............: " + formatarConta(numeroConta), quebra);
        adicionar(texto, " Emitido em .......: " + LocalDateTime.now(relogio).format(FORMATO_DATA_HORA),
                quebra);
        adicionar(texto, "-".repeat(LARGURA), quebra);
        adicionar(texto, String.format(FORMATO_LINHA, "DATA/HORA", "TIPO", "CONTA REL.", "VALOR (R$)"),
                quebra);
        adicionar(texto, "-".repeat(LARGURA), quebra);
        if (transacoes.isEmpty()) {
            adicionar(texto, " Nenhum movimento.", quebra);
        }
        for (Transacao transacao : transacoes) {
            adicionar(texto, linhaDeMovimento(numeroConta, transacao), quebra);
        }
        adicionar(texto, "-".repeat(LARGURA), quebra);
        adicionar(texto, " Movimentos: " + transacoes.size() + "    (+ entrada / - saída)", quebra);
        adicionar(texto, " Saldo atual: R$ " + formatarValor(saldoAtual), quebra);
        adicionar(texto, "=".repeat(LARGURA), quebra);
        return texto.toString();
    }

    private static void adicionar(StringBuilder texto, String linha, String quebra) {
        texto.append(linha).append(quebra);
    }

    private static String linhaDeMovimento(long numeroConta, Transacao transacao) {
        if (transacao == null) {
            throw new IllegalArgumentException("Uma das transações é nula.");
        }
        boolean eOrigem = Long.valueOf(numeroConta).equals(transacao.getContaOrigem());
        boolean eDestino = Long.valueOf(numeroConta).equals(transacao.getContaDestino());
        if (!eOrigem && !eDestino) {
            throw new IllegalArgumentException(
                    "A transação " + transacao.getId() + " não pertence à conta " + numeroConta + ".");
        }
        TipoTransacao tipo = transacao.getTipo();
        boolean entrada = tipo == TipoTransacao.DEPOSITO || (tipo == TipoTransacao.TRANSFERENCIA && eDestino);
        String relacionada = "-";
        if (tipo == TipoTransacao.TRANSFERENCIA) {
            relacionada = formatarConta(entrada ? transacao.getContaOrigem() : transacao.getContaDestino());
        }
        String rotulo = switch (tipo) {
            case DEPOSITO -> "DEPÓSITO";
            case SAQUE -> "SAQUE";
            case TRANSFERENCIA -> "TRANSFERÊNCIA";
        };
        String valor = (entrada ? "+" : "-") + formatarValor(transacao.getValor());
        return String.format(FORMATO_LINHA, transacao.getHorario().format(FORMATO_DATA_HORA), rotulo,
                relacionada, valor);
    }

    private static String formatarConta(long numero) {
        return String.format("%06d", numero);
    }

    private static String formatarValor(BigDecimal valor) {
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setDecimalSeparator(',');
        simbolos.setGroupingSeparator('.');
        DecimalFormat formato = new DecimalFormat("#,##0.00", simbolos);
        formato.setRoundingMode(RoundingMode.UNNECESSARY);
        try {
            return formato.format(valor);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "O valor " + valor.toPlainString() + " deve ter no máximo duas casas decimais.", e);
        }
    }
}
