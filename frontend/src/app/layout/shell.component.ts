import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, tap } from 'rxjs';
import { BancoApi } from '../core/api/banco-api';
import { IconeComponent, NomeIcone } from '../shared/ui/icone.component';

interface ItemMenu {
  rotulo: string;
  rota: string;
  icone: NomeIcone;
}

/**
 * Casca das telas autenticadas, fiel ao protótipo: barra lateral azul-marinho (marca, conta,
 * navegação, aviso e saída), topo com caminho de navegação e usuário, e rodapé.
 * No desktop a barra é fixa; abaixo de 1024px vira uma gaveta que desliza sobre o conteúdo.
 * Não contém regra bancária: só lê a conta pela porta `BancoApi`.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterLink, RouterLinkActive, RouterOutlet, IconeComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  private readonly router = inject(Router);
  private readonly api = inject(BancoApi);

  protected readonly menuAberto = signal(false);

  protected readonly itensMenu: readonly ItemMenu[] = [
    { rotulo: 'Visão geral', rota: '/visao-geral', icone: 'visao-geral' },
    { rotulo: 'Depósito', rota: '/deposito', icone: 'deposito' },
    { rotulo: 'Saque', rota: '/saque', icone: 'saque' },
    { rotulo: 'Transferência', rota: '/transferencia', icone: 'transferencia' },
    { rotulo: 'Extrato', rota: '/extrato', icone: 'extrato' },
  ];

  /** Conta logada. `undefined` enquanto carrega, o que mostra o esqueleto na barra e no topo. */
  protected readonly conta = toSignal(this.api.obterConta());

  protected readonly iniciais = computed(() => {
    const nome = this.conta()?.titular ?? '';
    return nome.split(' ').filter(Boolean).slice(0, 2).map((parte) => parte[0]).join('').toUpperCase();
  });

  protected readonly primeiroNome = computed(() => this.conta()?.titular.split(' ')[0] ?? '');

  /** Título da rota ativa, usado no caminho de navegação do topo. Fecha a gaveta ao navegar. */
  protected readonly titulo = toSignal(
    this.router.events.pipe(
      filter((evento): evento is NavigationEnd => evento instanceof NavigationEnd),
      tap(() => this.menuAberto.set(false)),
      map(() => this.tituloDaRotaAtiva()),
    ),
    { initialValue: '' },
  );

  protected alternarMenu(): void {
    this.menuAberto.update((aberto) => !aberto);
  }

  protected fecharMenu(): void {
    this.menuAberto.set(false);
  }

  protected sair(): void {
    void this.router.navigateByUrl('/login');
  }

  private tituloDaRotaAtiva(): string {
    let rota = this.router.routerState.snapshot.root;
    while (rota.firstChild) {
      rota = rota.firstChild;
    }
    return rota.data['titulo'] ?? '';
  }
}
