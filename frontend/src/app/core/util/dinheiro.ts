/**
 * Dinheiro no frontend.
 *
 * O backend trabalha com BigDecimal, então a API trafega valores como TEXTO decimal exato
 * ("2950.00"). Nunca convertemos para `number`: toda conta é feita em centavos (bigint) e
 * o formato pt-BR é montado a partir dos dígitos, sem ponto flutuante.
 */
export type Dinheiro = string;

const PADRAO = /^-?\d+\.\d{2}$/;

/** Texto exibido no lugar do valor quando o modo seguro está ligado. */
export const VALOR_OCULTO = 'R$ ••••••';

/** Converte "2950.00" em 295000n. Lança erro se o texto não tiver exatamente duas casas. */
export function paraCentavos(valor: Dinheiro): bigint {
  if (!PADRAO.test(valor)) {
    throw new Error(`Valor monetário inválido: "${valor}".`);
  }
  const negativo = valor.startsWith('-');
  const centavos = BigInt(valor.replace('-', '').replace('.', ''));
  return negativo ? -centavos : centavos;
}

/** Converte 295000n em "2950.00". */
export function deCentavos(centavos: bigint): Dinheiro {
  const negativo = centavos < 0n;
  const texto = (negativo ? -centavos : centavos).toString().padStart(3, '0');
  return `${negativo ? '-' : ''}${texto.slice(0, -2)}.${texto.slice(-2)}`;
}

/** Soma dois valores sem perda de precisão. */
export function somar(a: Dinheiro, b: Dinheiro): Dinheiro {
  return deCentavos(paraCentavos(a) + paraCentavos(b));
}

/** Subtrai `b` de `a` sem perda de precisão. */
export function subtrair(a: Dinheiro, b: Dinheiro): Dinheiro {
  return deCentavos(paraCentavos(a) - paraCentavos(b));
}

/** Compara dois valores: -1 se a < b, 0 se iguais, 1 se a > b. */
export function comparar(a: Dinheiro, b: Dinheiro): -1 | 0 | 1 {
  const diferenca = paraCentavos(a) - paraCentavos(b);
  return diferenca < 0n ? -1 : diferenca > 0n ? 1 : 0;
}

/** Formata como moeda brasileira: "R$ 2.950,00" (negativos: "− R$ 200,00"). */
export function formatarReal(valor: Dinheiro): string {
  const centavos = paraCentavos(valor);
  const negativo = centavos < 0n;
  const texto = (negativo ? -centavos : centavos).toString().padStart(3, '0');
  const inteiro = texto.slice(0, -2).replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return `${negativo ? '− ' : ''}R$ ${inteiro},${texto.slice(-2)}`;
}
