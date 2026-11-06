package app;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import domain.Cliente;
import domain.Conta;
import domain.SaldoInsuficienteException;
import domain.TipoTransacao;
import domain.Transacao;
import persistence.ContaRepository;
import persistence.ContaRepositorySqlite;
import persistence.FabricaConexao;
import persistence.TransacaoRepository;
import persistence.TransacaoRepositorySqlite;
import simulacao.OperacaoBancariaThread;

/**
 * Ponto de entrada do sistema POO Bank.
 *
 * <p>Monta as dependências concretas de persistência SQLite, recupera as contas existentes entre
 * execuções, executa a simulação concorrente em memória com {@link OperacaoBancariaThread} e a
 * simulação concorrente persistida com {@link ServicoBancario}, confere os saldos finais contra os
 * valores esperados e exporta o extrato da primeira conta em arquivo {@code .txt} UTF-8.</p>
 */
public final class Main {

    private static final String PASTA_PADRAO = "saida";
    private static final String NOME_BANCO = "poo-bank.db";
    private static final int THREADS_MEMORIA = 16;
    private static final int RODADAS_MEMORIA = 100;
    private static final int THREADS_PERSISTIDAS = 8;
    private static final int RODADAS_PERSISTIDAS = 2;
    private static final int TIMEOUT_SEGUNDOS = 30;

    private static final List<ClienteDemonstracao> CLIENTES_DEMO = List.of(
            new ClienteDemonstracao("Ana Souza", "111.111.111-11", new BigDecimal("1000.00")),
            new ClienteDemonstracao("Bruno Lima", "222.222.222-22", new BigDecimal("1000.00")),
            new ClienteDemonstracao("Carla Mendes", "333.333.333-33", new BigDecimal("1000.00"))
    );

    private Main() {
    }

    /**
     * Inicia a demonstração do sistema bancário concorrente.
     *
     * @param args argumento opcional com o caminho da pasta de saída (padrão: {@code saida})
     */
    public static void main(String[] args) {
        Path pastaSaida = args != null && args.length > 0 ? Path.of(args[0]) : Path.of(PASTA_PADRAO);
        PrintStream saidaUtf8 = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        executar(pastaSaida, saidaUtf8, Clock.systemDefaultZone());
    }

    /**
     * Executa o fluxo completo do sistema sobre a pasta de saída informada.
     *
     * @param pastaSaida diretório onde o banco SQLite e o extrato exportado serão gravados
     * @param saida fluxo de impressão do relatório de execução
     * @param relogio relógio usado para registrar transações e emitir o extrato
     * @throws IllegalArgumentException se algum argumento for nulo
     * @throws UncheckedIOException se a pasta de saída ou o arquivo de extrato não puderem ser criados
     */
    public static void executar(Path pastaSaida, PrintStream saida, Clock relogio) {
        if (pastaSaida == null || saida == null || relogio == null) {
            throw new IllegalArgumentException(
                    "A pasta de saída, o fluxo de impressão e o relógio são obrigatórios.");
        }
        criarPastaSaida(pastaSaida);

        Path arquivoBanco = pastaSaida.resolve(NOME_BANCO);
        FabricaConexao fabrica = FabricaConexao.paraArquivo(arquivoBanco);
        fabrica.inicializarSchemaCompleto();

        ContaRepository repositorioContas = new ContaRepositorySqlite(fabrica);
        TransacaoRepository repositorioTransacoes = new TransacaoRepositorySqlite(fabrica);
        ServicoBancario servico = new ServicoBancario(repositorioContas, repositorioTransacoes, relogio);
        ExportadorExtrato exportador = new ExportadorExtrato(relogio);

        saida.println("======================================================================");
        saida.println(" POO BANK - SISTEMA BANCÁRIO COM TRANSAÇÕES CONCORRENTES");
        saida.println("======================================================================");

        List<Long> numerosContas = recuperarOuAbrirContas(repositorioContas, servico, saida);
        executarSimulacaoEmMemoria(saida);
        executarSimulacaoPersistida(numerosContas, servico, repositorioContas, saida);

        long contaPrincipal = numerosContas.get(0);
        BigDecimal saldoFinal = servico.consultarSaldo(contaPrincipal);
        List<Transacao> extrato = servico.consultarExtrato(contaPrincipal);
        Path arquivoExtrato = pastaSaida.resolve(
                String.format("extrato-conta-%06d.txt", contaPrincipal));
        exportador.exportar(contaPrincipal, saldoFinal, extrato, arquivoExtrato);

        saida.println("----------------------------------------------------------------------");
        saida.println(" Extrato exportado: " + arquivoExtrato);
        saida.println(" Total de movimentos no extrato: " + extrato.size());
        saida.println("======================================================================");
    }

    private static void criarPastaSaida(Path pastaSaida) {
        try {
            Files.createDirectories(pastaSaida);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Não foi possível criar a pasta de saída em " + pastaSaida + ".", e);
        }
    }

    private static List<Long> recuperarOuAbrirContas(ContaRepository repositorioContas,
                                                     ServicoBancario servico,
                                                     PrintStream saida) {
        List<Conta> existentes = repositorioContas.listarTodas();
        saida.println(" Contas recuperadas do banco: " + existentes.size());
        for (Conta conta : existentes) {
            saida.printf("   [Recuperada] Conta %06d | Titular: %-14s | Saldo: R$ %s%n",
                    conta.getNumero(), conta.getTitular().getNome(),
                    conta.consultarSaldo().toPlainString());
        }

        List<Long> numeros = new ArrayList<>();
        for (ClienteDemonstracao demo : CLIENTES_DEMO) {
            Optional<Conta> encontrada = existentes.stream()
                    .filter(c -> c.getTitular().getDocumento().equals(demo.documento()))
                    .findFirst();
            if (encontrada.isPresent()) {
                numeros.add(encontrada.get().getNumero());
            } else {
                long numero = servico.abrirConta(demo.nome(), demo.documento(), demo.saldoInicial());
                numeros.add(numero);
                saida.printf("   [Nova Conta] Conta %06d | Titular: %-14s | Saldo inicial: R$ %s%n",
                        numero, demo.nome(), demo.saldoInicial().toPlainString());
            }
        }
        return numeros;
    }

    private static void executarSimulacaoEmMemoria(PrintStream saida) {
        saida.println("----------------------------------------------------------------------");
        saida.println(" Simulação A: Concorrência em memória (OperacaoBancariaThread)");

        BigDecimal saldoInicial = new BigDecimal("2500.00");
        Conta conta1 = new Conta(1L, new Cliente(1L, "Ana Souza", "111.111.111-11"), saldoInicial);
        Conta conta2 = new Conta(2L, new Cliente(2L, "Bruno Lima", "222.222.222-22"), saldoInicial);
        Conta conta3 = new Conta(3L, new Cliente(3L, "Carla Mendes", "333.333.333-33"), saldoInicial);

        List<TarefaMemoria> tarefas = new ArrayList<>();
        for (int i = 0; i < RODADAS_MEMORIA; i++) {
            tarefas.add(criarTarefaMemoria(TipoTransacao.DEPOSITO, null, conta1, new BigDecimal("10.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.SAQUE, conta1, null, new BigDecimal("5.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.TRANSFERENCIA, conta1, conta2, new BigDecimal("15.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.TRANSFERENCIA, conta2, conta1, new BigDecimal("8.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.TRANSFERENCIA, conta2, conta3, new BigDecimal("12.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.TRANSFERENCIA, conta3, conta1, new BigDecimal("6.00")));
            tarefas.add(criarTarefaMemoria(TipoTransacao.SAQUE, conta1, null, new BigDecimal("50000.00")));
        }

        dispararConcorrente(tarefas.stream().map(t -> (Runnable) t.operacao()).toList(), THREADS_MEMORIA);

        Map<Long, BigDecimal> esperados = new HashMap<>();
        esperados.put(1L, saldoInicial);
        esperados.put(2L, saldoInicial);
        esperados.put(3L, saldoInicial);

        int concluidas = 0;
        int rejeitadas = 0;
        for (TarefaMemoria tarefa : tarefas) {
            if (tarefa.operacao().foiConcluida()) {
                concluidas++;
                aplicarNoMapa(esperados, tarefa.tipo(),
                        tarefa.origem() != null ? tarefa.origem().getNumero() : null,
                        tarefa.destino() != null ? tarefa.destino().getNumero() : null,
                        tarefa.valor());
            } else if (tarefa.operacao().foiRejeitada()) {
                rejeitadas++;
            }
        }

        boolean consistente = true;
        for (Conta conta : List.of(conta1, conta2, conta3)) {
            BigDecimal esperado = esperados.get(conta.getNumero());
            BigDecimal obtido = conta.consultarSaldo();
            if (esperado.compareTo(obtido) != 0) {
                consistente = false;
            }
            saida.printf("   Conta %06d | Esperado: R$ %8s | Obtido: R$ %8s%n",
                    conta.getNumero(), esperado.toPlainString(), obtido.toPlainString());
        }
        saida.printf("   Operações: %d concluídas, %d rejeitadas%n", concluidas, rejeitadas);
        if (!consistente) {
            throw new IllegalStateException("Divergência de saldo na Simulação A.");
        }
        saida.println("   Resultado Simulação A: ESPERADO IGUAL AO OBTIDO");
    }

    private static TarefaMemoria criarTarefaMemoria(TipoTransacao tipo, Conta origem,
                                                    Conta destino, BigDecimal valor) {
        OperacaoBancariaThread operacao = new OperacaoBancariaThread(tipo, origem, destino, valor);
        return new TarefaMemoria(operacao, tipo, origem, destino, valor);
    }

    private static void executarSimulacaoPersistida(List<Long> numeros,
                                                    ServicoBancario servico,
                                                    ContaRepository repositorioContas,
                                                    PrintStream saida) {
        saida.println("----------------------------------------------------------------------");
        saida.println(" Simulação B: Concorrência persistida (ServicoBancario + SQLite)");

        long c1 = numeros.get(0);
        long c2 = numeros.get(1);
        long c3 = numeros.get(2);

        Map<Long, BigDecimal> esperados = new HashMap<>();
        for (long numero : numeros) {
            esperados.put(numero, servico.consultarSaldo(numero));
        }

        List<TarefaPersistida> tarefas = new ArrayList<>();
        for (int i = 0; i < RODADAS_PERSISTIDAS; i++) {
            tarefas.add(new TarefaPersistida(TipoTransacao.DEPOSITO, null, c1, new BigDecimal("250.00")));
            tarefas.add(new TarefaPersistida(TipoTransacao.SAQUE, c1, null, new BigDecimal("75.25")));
            tarefas.add(new TarefaPersistida(TipoTransacao.TRANSFERENCIA, c1, c2, new BigDecimal("60.00")));
            tarefas.add(new TarefaPersistida(TipoTransacao.TRANSFERENCIA, c2, c1, new BigDecimal("25.50")));
            tarefas.add(new TarefaPersistida(TipoTransacao.TRANSFERENCIA, c2, c3, new BigDecimal("40.00")));
            tarefas.add(new TarefaPersistida(TipoTransacao.TRANSFERENCIA, c3, c1, new BigDecimal("15.00")));
            tarefas.add(new TarefaPersistida(TipoTransacao.SAQUE, c1, null, new BigDecimal("999999.00")));
        }

        List<Runnable> runnables = tarefas.stream()
                .map(tarefa -> (Runnable) () -> tarefa.executar(servico))
                .toList();
        dispararConcorrente(runnables, THREADS_PERSISTIDAS);

        int concluidas = 0;
        int rejeitadas = 0;
        for (TarefaPersistida tarefa : tarefas) {
            if (tarefa.concluida.get()) {
                concluidas++;
                aplicarNoMapa(esperados, tarefa.tipo, tarefa.origem, tarefa.destino, tarefa.valor);
            } else if (tarefa.rejeitada.get()) {
                rejeitadas++;
            }
        }

        boolean consistente = true;
        for (long numero : numeros) {
            BigDecimal esperado = esperados.get(numero);
            BigDecimal memoria = servico.consultarSaldo(numero);
            BigDecimal banco = repositorioContas.buscarPorNumero(numero)
                    .orElseThrow()
                    .consultarSaldo();
            if (esperado.compareTo(memoria) != 0 || esperado.compareTo(banco) != 0) {
                consistente = false;
            }
            saida.printf("   Conta %06d | Esperado: R$ %8s | Memória: R$ %8s | Banco: R$ %8s%n",
                    numero, esperado.toPlainString(), memoria.toPlainString(), banco.toPlainString());
        }
        saida.printf("   Operações: %d concluídas, %d rejeitadas%n", concluidas, rejeitadas);
        if (!consistente) {
            throw new IllegalStateException("Divergência de saldo na Simulação B.");
        }
        saida.println("   Resultado Simulação B: ESPERADO IGUAL AO OBTIDO (MEMÓRIA E BANCO)");
    }

    private static void aplicarNoMapa(Map<Long, BigDecimal> saldos, TipoTransacao tipo,
                                      Long origem, Long destino, BigDecimal valor) {
        switch (tipo) {
            case DEPOSITO -> saldos.put(destino, saldos.get(destino).add(valor));
            case SAQUE -> saldos.put(origem, saldos.get(origem).subtract(valor));
            case TRANSFERENCIA -> {
                saldos.put(origem, saldos.get(origem).subtract(valor));
                saldos.put(destino, saldos.get(destino).add(valor));
            }
        }
    }

    private static void dispararConcorrente(List<Runnable> tarefas, int numeroThreads) {
        ExecutorService executor = Executors.newFixedThreadPool(numeroThreads);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<?>> futuros = new ArrayList<>(tarefas.size());
        try {
            for (Runnable tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    try {
                        largada.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Thread interrompida antes da largada.", e);
                    }
                    tarefa.run();
                }));
            }
            largada.countDown();
            for (Future<?> futuro : futuros) {
                futuro.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Execução concorrente interrompida.", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha durante a execução concorrente.", e.getCause());
        } catch (TimeoutException e) {
            throw new IllegalStateException("Tempo esgotado na simulação concorrente (possível deadlock).", e);
        } finally {
            executor.shutdownNow();
        }
    }

    private record ClienteDemonstracao(String nome, String documento, BigDecimal saldoInicial) {
    }

    private record TarefaMemoria(OperacaoBancariaThread operacao, TipoTransacao tipo,
                                 Conta origem, Conta destino, BigDecimal valor) {
    }

    private static final class TarefaPersistida {
        private final TipoTransacao tipo;
        private final Long origem;
        private final Long destino;
        private final BigDecimal valor;
        private final AtomicBoolean concluida = new AtomicBoolean(false);
        private final AtomicBoolean rejeitada = new AtomicBoolean(false);

        private TarefaPersistida(TipoTransacao tipo, Long origem, Long destino, BigDecimal valor) {
            this.tipo = tipo;
            this.origem = origem;
            this.destino = destino;
            this.valor = valor;
        }

        private void executar(ServicoBancario servico) {
            try {
                switch (tipo) {
                    case DEPOSITO -> servico.depositar(destino, valor);
                    case SAQUE -> servico.sacar(origem, valor);
                    case TRANSFERENCIA -> servico.transferir(origem, destino, valor);
                }
                concluida.set(true);
            } catch (SaldoInsuficienteException e) {
                rejeitada.set(true);
            }
        }
    }
}