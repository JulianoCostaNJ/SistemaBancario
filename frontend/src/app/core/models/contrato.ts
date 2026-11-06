import { Dinheiro } from '../util/dinheiro';

/**
 * Contrato de dados entre o frontend e o backend do POO Bank.
 *
 * Espelha as tabelas do banco (schema.sql e schema-banco-digital.sql). Valores monetários são
 * sempre texto decimal exato (`Dinheiro`), nunca `number`. Datas e horários são ISO 8601.
 * Este arquivo só descreve formatos: não contém regra de negócio.
 */

/** Espelha domain.TipoTransacao. */
export type TipoTransacao = 'DEPOSITO' | 'SAQUE' | 'TRANSFERENCIA';

/** Do ponto de vista da conta logada: dinheiro que entrou ou que saiu. */
export type Sentido = 'ENTRADA' | 'SAIDA';

export interface ContaResumo {
  /** Chave interna (contas.numero), usada pelo backend para ordenar bloqueios. */
  numero: number;
  agencia: string;
  /** Parte exibida do número (dados_conta.numero_exibicao), por exemplo "10482". */
  numeroExibicao: string;
  digito: string;
  titular: string;
  saldo: Dinheiro;
}

export interface Categoria {
  id: number;
  nome: string;
  /** Nome do ícone (por exemplo "heart-pulse"), resolvido pela camada visual. */
  icone: string;
  cor: string;
}

/** Uma linha do extrato, já vista da conta logada. */
export interface Movimento {
  id: number;
  tipo: TipoTransacao;
  sentido: Sentido;
  valor: Dinheiro;
  /** ISO 8601 sem fuso, por exemplo "2026-11-05T10:20:00". */
  horario: string;
  descricao: string;
  /** Nome da outra ponta (transferências) ou do cartão (pagamento de fatura). */
  contraparte: string | null;
  categoria: Categoria | null;
}
