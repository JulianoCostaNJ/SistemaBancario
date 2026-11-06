import { Observable } from 'rxjs';
import { ContaResumo, Movimento } from '../models/contrato';

/** Falha de negócio devolvida pela API, com um código estável para a tela tratar. */
export class ErroApi extends Error {
  constructor(
    readonly codigo: 'CREDENCIAIS_INVALIDAS' | 'SALDO_INSUFICIENTE' | 'CONTA_INEXISTENTE' | 'ERRO_DE_REDE',
    mensagem: string,
  ) {
    super(mensagem);
    this.name = 'ErroApi';
  }
}

/**
 * Porta de acesso ao backend. As telas dependem SÓ desta classe abstrata: hoje a implementação é
 * `BancoApiMock`, depois será trocada por uma implementação HTTP sem mexer em nenhum componente.
 * Os métodos de cada tela (depositar, sacar, transferir, cartões...) entram conforme as etapas.
 */
export abstract class BancoApi {
  /** Autentica pelo número exibido da conta (por exemplo "10482-7") e pela senha. */
  abstract entrar(contaComDigito: string, senha: string): Observable<ContaResumo>;

  /** Devolve a conta logada, com o saldo atual. */
  abstract obterConta(): Observable<ContaResumo>;

  /** Extrato da conta logada, em ordem cronológica. */
  abstract listarMovimentos(): Observable<Movimento[]>;
}
