package simulacao;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * EXPERIMENTO DIDÁTICO (evidência para o relatório) — não é um teste automatizado.
 *
 * <p>Executa várias vezes a mesma simulação de depósitos concorrentes sobre uma única conta e
 * compara o saldo esperado com o saldo obtido. Serve para mostrar a <b>race condition</b> antes
 * da sincronização de {@link Conta} e o comportamento correto depois dela. O programa informa,
 * por reflexão, se os métodos de saldo estão {@code synchronized}, para que a saída seja
 * autoexplicativa em um print.</p>
 *
 * <p>Execução (na raiz do projeto, depois de {@code mvn test-compile}):
 * {@code java -cp target/classes;target/test-classes simulacao.ExperimentoRaceCondition}
 * (no Linux/macOS, troque {@code ;} por {@code :}).</p>
 */
public final class ExperimentoRaceCondition {

    private static final int EXECUCOES = 3;
    private static final int THREADS = 32;
    private static final int OPERACOES = 20_000;
    private static final BigDecimal VALOR = new BigDecimal("1.00");

    private ExperimentoRaceCondition() {
    }

    /**
     * Ponto de entrada do experimento.
     *
     * @param args não utilizado
     * @throws Exception se a simulação falhar
     */
    public static void main(String[] args) throws Exception {
        System.out.println("=======================================================================");
        System.out.println(" EXPERIMENTO DE CONCORRENCIA - DEPOSITOS SIMULTANEOS NA MESMA CONTA");
        System.out.println("=======================================================================");
        imprimirEstadoDaSincronizacao();
        System.out.println();

        BigDecimal esperado = VALOR.multiply(BigDecimal.valueOf(OPERACOES));
        int corrompidas = 0;
        for (int execucao = 1; execucao <= EXECUCOES; execucao++) {
            Conta conta = new Conta(1L, new Cliente(1L, "Ana Souza", "123.456.789-09"),
                    new BigDecimal("0.00"));
            List<OperacaoBancariaThread> operacoes = new ArrayList<>();
            for (int i = 0; i < OPERACOES; i++) {
                operacoes.add(new OperacaoBancariaThread(TipoTransacao.DEPOSITO, null, conta, VALOR));
            }

            OperacaoBancariaThreadTest.executar(operacoes, THREADS);

            BigDecimal obtido = conta.consultarSaldo();
            boolean correto = obtido.compareTo(esperado) == 0;
            if (!correto) {
                corrompidas++;
            }
            System.out.printf("Execucao %d/%d | Threads: %d | Operacoes executadas: %d (deposito de R$ %s)%n",
                    execucao, EXECUCOES, THREADS, OPERACOES, VALOR.toPlainString());
            System.out.printf("   Saldo Esperado: R$ %s%n", esperado.toPlainString());
            System.out.printf("   Saldo Final Obtido: R$ %s  -> %s%n", obtido.toPlainString(),
                    correto ? "CORRETO" : "CORROMPIDO (perdeu R$ "
                            + esperado.subtract(obtido).toPlainString() + ")");
            System.out.println();
        }
        System.out.printf("Resumo: %d de %d execucoes com saldo corrompido.%n", corrompidas, EXECUCOES);
    }

    private static void imprimirEstadoDaSincronizacao() throws NoSuchMethodException {
        Method[] metodos = {
            Conta.class.getMethod("consultarSaldo"),
            Conta.class.getMethod("depositar", BigDecimal.class),
            Conta.class.getMethod("sacar", BigDecimal.class)
        };
        System.out.println("Estado da classe Conta (verificado por reflexao):");
        for (Method metodo : metodos) {
            System.out.printf("   %-16s synchronized = %s%n", metodo.getName() + "()",
                    Modifier.isSynchronized(metodo.getModifiers()));
        }
    }
}
