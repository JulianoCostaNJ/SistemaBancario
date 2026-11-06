import { firstValueFrom } from 'rxjs';
import { ErroApi } from './banco-api';
import { BancoApiMock } from './banco-api.mock';

describe('BancoApiMock', () => {
  const api = new BancoApiMock();

  it('autentica com a conta e a senha de demonstração', async () => {
    const conta = await firstValueFrom(api.entrar('10482-7', 'poobank@2026'));
    expect(conta.titular).toBe('Mariana Costa');
    expect(conta.saldo).toBe('2950.00');
  });

  it('recusa credenciais inválidas com um código estável', async () => {
    await expect(firstValueFrom(api.entrar('10482-7', 'errada'))).rejects.toMatchObject({
      codigo: 'CREDENCIAIS_INVALIDAS',
    });
    await expect(firstValueFrom(api.entrar('99999-9', 'poobank@2026'))).rejects.toBeInstanceOf(ErroApi);
  });

  it('devolve o extrato em ordem cronológica, com os movimentos do protótipo', async () => {
    const movimentos = await firstValueFrom(api.listarMovimentos());
    const horarios = movimentos.map((m) => m.horario);
    expect(horarios).toEqual([...horarios].sort());
    expect(movimentos.at(-1)).toMatchObject({ tipo: 'SAQUE', valor: '200.00', sentido: 'SAIDA' });
    expect(movimentos.at(-2)).toMatchObject({ tipo: 'TRANSFERENCIA', contraparte: 'Lucas Almeida' });
  });
});
