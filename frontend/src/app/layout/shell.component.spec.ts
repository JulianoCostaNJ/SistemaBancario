import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BancoApi } from '../core/api/banco-api';
import { ContaResumo } from '../core/models/contrato';
import { ShellComponent } from './shell.component';

const CONTA: ContaResumo = {
  numero: 1, agencia: '0001', numeroExibicao: '10482', digito: '7', titular: 'Mariana Costa', saldo: '2950.00',
};

describe('ShellComponent', () => {
  async function montar() {
    await TestBed.configureTestingModule({
      imports: [ShellComponent],
      providers: [provideRouter([]), { provide: BancoApi, useValue: { obterConta: () => of(CONTA) } }],
    }).compileComponents();
    const fixture = TestBed.createComponent(ShellComponent);
    await fixture.whenStable();
    return fixture;
  }

  it('mostra os cinco itens de navegação do protótipo', async () => {
    const html = (await montar()).nativeElement as HTMLElement;
    const rotulos = Array.from(html.querySelectorAll('aside nav a')).map((a) => a.textContent?.trim());
    expect(rotulos).toEqual(['Visão geral', 'Depósito', 'Saque', 'Transferência', 'Extrato']);
  });

  it('mostra o titular e a conta com dígito na barra lateral', async () => {
    const html = (await montar()).nativeElement as HTMLElement;
    const barra = html.querySelector('aside')?.textContent ?? '';
    expect(barra).toContain('Mariana Costa');
    expect(barra).toContain('Conta 10482-7');
  });

  it('mostra as iniciais do titular no topo', async () => {
    const html = (await montar()).nativeElement as HTMLElement;
    expect(html.querySelector('header')?.textContent).toContain('MC');
  });

  it('abre e fecha a gaveta no mobile pelo botão do topo', async () => {
    const fixture = await montar();
    const html = fixture.nativeElement as HTMLElement;
    const barra = html.querySelector('aside') as HTMLElement;
    const botao = html.querySelector('header button') as HTMLButtonElement;

    expect(barra.classList.contains('-translate-x-full')).toBe(true);
    expect(botao.getAttribute('aria-expanded')).toBe('false');

    botao.click();
    await fixture.whenStable();
    expect(barra.classList.contains('-translate-x-full')).toBe(false);
    expect(botao.getAttribute('aria-expanded')).toBe('true');

    botao.click();
    await fixture.whenStable();
    expect(barra.classList.contains('-translate-x-full')).toBe(true);
  });
});
