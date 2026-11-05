package app;

import domain.Cliente;
import domain.Conta;
import domain.TipoTransacao;
import domain.Transacao;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import persistence.ContaRepository;
import persistence.TransacaoRepository;

/**
 * Serviço de aplicação: coordena os casos de uso do banco, unindo o domínio e a persistência.
 *
 * <p>O serviço depende apenas das interfaces {@link ContaRepository} e
 * {@link TransacaoRepository}. A API trabalha com números de conta, nunca devolve o objeto
 * {@link Conta}: assim ninguém altera um saldo em memória sem que a operação seja gravada.</p>
 *
 * <p><b>Consistência entre memória e banco.</b> Cada operação segue três passos: executar a
 * regra no domínio, criar a {@link Transacao} e gravá-la, junto com os saldos atuais das contas,
 * em uma única operação atômica. Para que o banco nunca receba um saldo mais antigo depois de um
 * mais novo, todos os métodos públicos usam o mesmo bloqueio do serviço, do início até a
 * confirmação da gravação. Esse bloqueio é diferente do monitor de cada {@link Conta}, que
 * continua curto e protege apenas o saldo em memória. Como o SQLite aceita um único escritor por
 * vez, o bloqueio custa pouco paralelismo. Se a gravação falhar, as contas envolvidas são
 * descartadas da memória e recarregadas do banco no próximo uso, o que desfaz a divergência.</p>
 *
 * <p>Deve existir <b>uma única instância</b> do serviço por banco: instâncias diferentes teriam
 * memórias separadas.</p>
 */
public final class ServicoBancario {

    private final ContaRepository contas;
    private final TransacaoRepository transacoes;
    private final Clock relogio;
    private final Map<Long, Conta> contasEmMemoria = new HashMap<>();

    /**
     * Cria o serviço.
     *
     * @param contas repositório de contas e titulares
     * @param transacoes repositório de transações
     * @param relogio relógio que fornece o horário das transações
     * @throws IllegalArgumentException se alguma dependência for nula
     */
    public ServicoBancario(ContaRepository contas, TransacaoRepository transacoes, Clock relogio) {
        if (contas == null || transacoes == null || relogio == null) {
            throw new IllegalArgumentException("As dependências do serviço são obrigatórias.");
        }
        this.contas = contas;
        this.transacoes = transacoes;
        this.relogio = relogio;
    }

    /**
     * Abre uma conta nova para um cliente novo. O número da conta e o identificador do cliente são
     * gerados pelo sistema. O saldo inicial não gera transação.
     *
     * @param nome nome do titular
     * @param documento documento do titular, que não pode repetir o de outro cliente
     * @param saldoInicial saldo inicial, não negativo e com no máximo duas casas decimais
     * @return número da conta criada
     * @throws IllegalArgumentException se o nome, o documento ou o saldo forem inválidos
     * @throws persistence.PersistenciaException se o documento já estiver cadastrado ou se a
     *         gravação falhar
     */
    public synchronized long abrirConta(String nome, String documento, BigDecimal saldoInicial) {
        Cliente titular = new Cliente(contas.proximoIdCliente(), nome, documento);
        Conta conta = new Conta(contas.proximoNumeroConta(), titular, saldoInicial);
        contas.inserir(conta);
        contasEmMemoria.put(conta.getNumero(), conta);
        return conta.getNumero();
    }

    /**
     * Deposita um valor em uma conta e grava a transação.
     *
     * @param numeroConta número da conta
     * @param valor valor positivo, com no máximo duas casas decimais
     * @return a transação gravada
     * @throws IllegalArgumentException se a conta não existir ou o valor for inválido
     * @throws persistence.PersistenciaException se a gravação falhar; o saldo volta ao do banco
     */
    public synchronized Transacao depositar(long numeroConta, BigDecimal valor) {
        Conta conta = obterConta(numeroConta);
        conta.depositar(valor);
        return gravar(TipoTransacao.DEPOSITO, null, numeroConta, valor, List.of(conta));
    }

    /**
     * Saca um valor de uma conta e grava a transação.
     *
     * @param numeroConta número da conta
     * @param valor valor positivo, com no máximo duas casas decimais
     * @return a transação gravada
     * @throws IllegalArgumentException se a conta não existir ou o valor for inválido
     * @throws domain.SaldoInsuficienteException se o saldo for menor que o valor; nada é gravado
     * @throws persistence.PersistenciaException se a gravação falhar; o saldo volta ao do banco
     */
    public synchronized Transacao sacar(long numeroConta, BigDecimal valor) {
        Conta conta = obterConta(numeroConta);
        conta.sacar(valor);
        return gravar(TipoTransacao.SAQUE, numeroConta, null, valor, List.of(conta));
    }

    /**
     * Transfere um valor entre duas contas e grava uma única transação.
     *
     * @param numeroOrigem número da conta debitada
     * @param numeroDestino número da conta creditada, diferente da origem
     * @param valor valor positivo, com no máximo duas casas decimais
     * @return a transação gravada
     * @throws IllegalArgumentException se alguma conta não existir, se forem a mesma conta ou se o
     *         valor for inválido
     * @throws domain.SaldoInsuficienteException se o saldo da origem for menor que o valor; nada é
     *         gravado
     * @throws persistence.PersistenciaException se a gravação falhar; os saldos voltam aos do
     *         banco
     */
    public synchronized Transacao transferir(long numeroOrigem, long numeroDestino, BigDecimal valor) {
        Conta origem = obterConta(numeroOrigem);
        Conta destino = obterConta(numeroDestino);
        origem.transferirPara(destino, valor);
        return gravar(TipoTransacao.TRANSFERENCIA, numeroOrigem, numeroDestino, valor,
                List.of(origem, destino));
    }

    /**
     * Consulta o saldo atual de uma conta.
     *
     * @param numeroConta número da conta
     * @return saldo com duas casas decimais
     * @throws IllegalArgumentException se a conta não existir
     */
    public synchronized BigDecimal consultarSaldo(long numeroConta) {
        return obterConta(numeroConta).consultarSaldo();
    }

    /**
     * Consulta o extrato de uma conta, em ordem cronológica.
     *
     * @param numeroConta número da conta
     * @return transações em que a conta é origem ou destino, possivelmente vazia
     * @throws IllegalArgumentException se a conta não existir
     * @throws persistence.PersistenciaException se a consulta falhar
     */
    public synchronized List<Transacao> consultarExtrato(long numeroConta) {
        obterConta(numeroConta);
        return transacoes.consultarExtrato(numeroConta);
    }

    private Conta obterConta(long numero) {
        Conta emMemoria = contasEmMemoria.get(numero);
        if (emMemoria != null) {
            return emMemoria;
        }
        Conta doBanco = contas.buscarPorNumero(numero)
                .orElseThrow(() -> new IllegalArgumentException("A conta " + numero + " não existe."));
        contasEmMemoria.put(numero, doBanco);
        return doBanco;
    }

    private Transacao gravar(TipoTransacao tipo, Long origem, Long destino, BigDecimal valor,
                             List<Conta> afetadas) {
        try {
            LocalDateTime horario = LocalDateTime.now(relogio).truncatedTo(ChronoUnit.MICROS);
            Transacao transacao = new Transacao(transacoes.proximoId(), origem, destino, tipo,
                    valor, horario);
            transacoes.registrarOperacao(transacao, afetadas);
            return transacao;
        } catch (RuntimeException e) {
            for (Conta conta : afetadas) {
                contasEmMemoria.remove(conta.getNumero());
            }
            throw e;
        }
    }
}
