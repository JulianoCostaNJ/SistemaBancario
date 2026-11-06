import { comparar, deCentavos, formatarReal, paraCentavos, somar, subtrair } from './dinheiro';

describe('dinheiro', () => {
  it('converte texto decimal em centavos e de volta', () => {
    expect(paraCentavos('2950.00')).toBe(295000n);
    expect(deCentavos(295000n)).toBe('2950.00');
    expect(deCentavos(5n)).toBe('0.05');
    expect(deCentavos(-20000n)).toBe('-200.00');
  });

  it('rejeita texto sem exatamente duas casas decimais', () => {
    expect(() => paraCentavos('10')).toThrow();
    expect(() => paraCentavos('10.5')).toThrow();
    expect(() => paraCentavos('10.001')).toThrow();
    expect(() => paraCentavos('abc')).toThrow();
  });

  it('soma sem a perda de precisão do ponto flutuante', () => {
    let total = '0.00';
    for (let i = 0; i < 10; i++) {
      total = somar(total, '0.10');
    }
    expect(total).toBe('1.00');
    expect(subtrair('2950.00', '200.00')).toBe('2750.00');
  });

  it('compara valores', () => {
    expect(comparar('10.00', '9.99')).toBe(1);
    expect(comparar('10.00', '10.00')).toBe(0);
    expect(comparar('0.01', '0.02')).toBe(-1);
  });

  it('formata em reais no padrão brasileiro', () => {
    expect(formatarReal('2950.00')).toBe('R$ 2.950,00');
    expect(formatarReal('1240.00')).toBe('R$ 1.240,00');
    expect(formatarReal('0.05')).toBe('R$ 0,05');
    expect(formatarReal('1234567.89')).toBe('R$ 1.234.567,89');
    expect(formatarReal('-200.00')).toBe('− R$ 200,00');
  });
});
