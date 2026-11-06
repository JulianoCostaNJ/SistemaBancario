import { Injectable } from '@angular/core';
import { Observable, delay, of, throwError } from 'rxjs';
import { Categoria, ContaResumo, Movimento } from '../models/contrato';
import { BancoApi, ErroApi } from './banco-api';

const CATEGORIAS: Record<string, Categoria> = {
  deposito: { id: 6, nome: 'Depósito', icone: 'arrow-down-left', cor: '#16A34A' },
  saque: { id: 7, nome: 'Saque', icone: 'banknote', cor: '#475569' },
  transferencia: { id: 8, nome: 'Transferência', icone: 'arrow-left-right', cor: '#0EA5E9' },
  fatura: { id: 9, nome: 'Fatura do cartão', icone: 'credit-card', cor: '#64748B' },
};

/**
 * Implementação em memória de `BancoApi`, com os mesmos dados do seed do backend
 * (seed-banco-digital.sql): Mariana Costa, conta 0001 10482-7, saldo R$ 2.950,00.
 * Simula a latência da rede para que os estados de loading sejam exercitados.
 */
@Injectable()
export class BancoApiMock extends BancoApi {
  /** Senha de demonstração, a mesma do seed. */
  private static readonly SENHA = 'poobank@2026';
  private static readonly LATENCIA_MS = 300;

  private readonly conta: ContaResumo = {
    numero: 1,
    agencia: '0001',
    numeroExibicao: '10482',
    digito: '7',
    titular: 'Mariana Costa',
    saldo: '2950.00',
  };

  private readonly movimentos: Movimento[] = [
    { id: 1, tipo: 'SAQUE', sentido: 'SAIDA', valor: '890.00', horario: '2026-08-11T10:05:00',
      descricao: 'Pagamento de fatura - Julho 2026', contraparte: 'Visa Platinum 4821', categoria: CATEGORIAS['fatura'] },
    { id: 2, tipo: 'SAQUE', sentido: 'SAIDA', valor: '1120.00', horario: '2026-09-12T09:40:00',
      descricao: 'Pagamento de fatura - Agosto 2026', contraparte: 'Visa Platinum 4821', categoria: CATEGORIAS['fatura'] },
    { id: 3, tipo: 'SAQUE', sentido: 'SAIDA', valor: '980.00', horario: '2026-10-14T16:20:00',
      descricao: 'Pagamento de fatura - Setembro 2026', contraparte: 'Visa Platinum 4821', categoria: CATEGORIAS['fatura'] },
    { id: 4, tipo: 'DEPOSITO', sentido: 'ENTRADA', valor: '1500.00', horario: '2026-11-03T09:15:00',
      descricao: 'Depósito na conta', contraparte: null, categoria: CATEGORIAS['deposito'] },
    { id: 5, tipo: 'TRANSFERENCIA', sentido: 'SAIDA', valor: '350.00', horario: '2026-11-04T14:30:00',
      descricao: 'Transferência enviada', contraparte: 'Lucas Almeida', categoria: CATEGORIAS['transferencia'] },
    { id: 6, tipo: 'SAQUE', sentido: 'SAIDA', valor: '200.00', horario: '2026-11-05T10:20:00',
      descricao: 'Saque simulado', contraparte: null, categoria: CATEGORIAS['saque'] },
  ];

  override entrar(contaComDigito: string, senha: string): Observable<ContaResumo> {
    const informada = contaComDigito.trim();
    const esperada = `${this.conta.numeroExibicao}-${this.conta.digito}`;
    if (informada !== esperada || senha !== BancoApiMock.SENHA) {
      return throwError(() => new ErroApi('CREDENCIAIS_INVALIDAS', 'Conta ou senha inválidas.'))
        .pipe(delay(BancoApiMock.LATENCIA_MS));
    }
    return of({ ...this.conta }).pipe(delay(BancoApiMock.LATENCIA_MS));
  }

  override obterConta(): Observable<ContaResumo> {
    return of({ ...this.conta }).pipe(delay(BancoApiMock.LATENCIA_MS));
  }

  override listarMovimentos(): Observable<Movimento[]> {
    return of(this.movimentos.map((movimento) => ({ ...movimento }))).pipe(delay(BancoApiMock.LATENCIA_MS));
  }
}
