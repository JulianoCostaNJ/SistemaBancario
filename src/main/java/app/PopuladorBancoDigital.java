package app;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.TransacaoRepositorySqlite;

/**
 * Cria o banco de demonstração do banco digital, o mesmo que o frontend Angular vai consumir.
 *
 * <p>Ponto de composição, como {@link Main}: monta as implementações SQLite. Toda a parte
 * financeira (saldos e transações) passa pelo {@link ServicoBancario}, com um relógio ajustável
 * para que cada operação tenha a data do protótipo. Assim {@code Conta} continua sendo a única a
 * alterar saldo. Os demais dados (categorias, cartões, faturas, Pix, metas) são gravados por
 * {@link FabricaConexao#popularDadosBancoDigital()}, e nenhum SQL existe nesta classe.</p>
 *
 * <p>Usa um arquivo próprio ({@code poo-bank-digital.db}), separado do banco da simulação de
 * {@link Main}. Se o banco já tiver contas, nada é alterado.</p>
 */
public final class PopuladorBancoDigital {

    private static final String PASTA_PADRAO = "saida";
    private static final String NOME_BANCO = "poo-bank-digital.db";

    private PopuladorBancoDigital() {
    }

    /**
     * Popula o banco de demonstração.
     *
     * @param args argumento opcional com a pasta de saída (padrão: {@code saida})
     */
    public static void main(String[] args) {
        Path pasta = args != null && args.length > 0 ? Path.of(args[0]) : Path.of(PASTA_PADRAO);
        executar(pasta, new PrintStream(System.out, true, StandardCharsets.UTF_8));
    }

    /**
     * Cria o esquema completo e grava o histórico de demonstração, se o banco estiver vazio.
     *
     * <p>Os valores são referenciais e seguem o protótipo: no fim, Mariana Costa tem saldo de
     * R$ 2.950,00 e Lucas Almeida, R$ 1.850,00.</p>
     *
     * @param pastaSaida pasta onde o arquivo do banco será criado
     * @param saida fluxo de impressão do relatório
     * @throws IllegalArgumentException se algum argumento for nulo
     * @throws UncheckedIOException se a pasta não puder ser criada
     */
    public static void executar(Path pastaSaida, PrintStream saida) {
        if (pastaSaida == null || saida == null) {
            throw new IllegalArgumentException("A pasta de saída e o fluxo de impressão são obrigatórios.");
        }
        try {
            Files.createDirectories(pastaSaida);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível criar a pasta " + pastaSaida + ".", e);
        }

        FabricaConexao fabrica = FabricaConexao.paraArquivo(pastaSaida.resolve(NOME_BANCO));
        fabrica.inicializarSchemaCompleto();
        ContaRepositorySqlite contas = new ContaRepositorySqlite(fabrica);
        int existentes = contas.listarTodas().size();
        if (existentes > 0) {
            saida.println("Banco digital já foi populado (" + existentes + " contas). Nada a fazer.");
            return;
        }

        RelogioAjustavel relogio = new RelogioAjustavel();
        ServicoBancario servico =
                new ServicoBancario(contas, new TransacaoRepositorySqlite(fabrica), relogio);

        relogio.definir(LocalDateTime.of(2026, 7, 1, 9, 0));
        long mariana = servico.abrirConta("Mariana Costa", "321.654.987-00", new BigDecimal("4990.00"));
        long lucas = servico.abrirConta("Lucas Almeida", "456.789.123-00", new BigDecimal("1500.00"));

        relogio.definir(LocalDateTime.of(2026, 8, 11, 10, 5));
        servico.sacar(mariana, new BigDecimal("890.00"));
        relogio.definir(LocalDateTime.of(2026, 9, 12, 9, 40));
        servico.sacar(mariana, new BigDecimal("1120.00"));
        relogio.definir(LocalDateTime.of(2026, 10, 14, 16, 20));
        servico.sacar(mariana, new BigDecimal("980.00"));
        relogio.definir(LocalDateTime.of(2026, 11, 3, 9, 15));
        servico.depositar(mariana, new BigDecimal("1500.00"));
        relogio.definir(LocalDateTime.of(2026, 11, 4, 14, 30));
        servico.transferir(mariana, lucas, new BigDecimal("350.00"));
        relogio.definir(LocalDateTime.of(2026, 11, 5, 10, 20));
        servico.sacar(mariana, new BigDecimal("200.00"));

        fabrica.popularDadosBancoDigital();

        saida.println("Banco digital populado em " + pastaSaida.resolve(NOME_BANCO));
        saida.printf("   Mariana Costa (conta %d): saldo R$ %s%n", mariana,
                servico.consultarSaldo(mariana).toPlainString());
        saida.printf("   Lucas Almeida (conta %d): saldo R$ %s%n", lucas,
                servico.consultarSaldo(lucas).toPlainString());
    }

    /** Relógio cujo instante é definido pelo chamador, para datar as operações da demonstração. */
    private static final class RelogioAjustavel extends Clock {

        private Instant instante = Instant.EPOCH;

        private void definir(LocalDateTime momento) {
            instante = momento.toInstant(ZoneOffset.UTC);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            throw new UnsupportedOperationException("Este relógio só opera em UTC.");
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
