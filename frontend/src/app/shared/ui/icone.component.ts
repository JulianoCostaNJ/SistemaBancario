import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Ícones disponíveis. Novos ícones entram aqui conforme as telas precisarem. */
export type NomeIcone =
  | 'visao-geral' | 'deposito' | 'saque' | 'transferencia' | 'extrato'
  | 'sair' | 'escudo' | 'olho' | 'olho-riscado' | 'menu' | 'fechar' | 'seta-direita';

/**
 * Ícone vetorial em linha (traço de 2px, grade 24x24, no estilo Lucide).
 * Herda a cor do texto (`currentColor`) e é decorativo por padrão (`aria-hidden`):
 * quem usa deve garantir o rótulo acessível no elemento pai.
 */
@Component({
  selector: 'app-icone',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'inline-flex shrink-0' },
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"
         [attr.width]="tamanho()" [attr.height]="tamanho()">
      @switch (nome()) {
        @case ('visao-geral') {
          <rect width="7" height="9" x="3" y="3" rx="1" />
          <rect width="7" height="5" x="14" y="3" rx="1" />
          <rect width="7" height="9" x="14" y="12" rx="1" />
          <rect width="7" height="5" x="3" y="16" rx="1" />
        }
        @case ('deposito') {
          <path d="M17 7 7 17" />
          <path d="M17 17H7V7" />
        }
        @case ('saque') {
          <rect width="20" height="12" x="2" y="6" rx="2" />
          <circle cx="12" cy="12" r="2" />
          <path d="M6 12h.01M18 12h.01" />
        }
        @case ('transferencia') {
          <path d="m16 3 4 4-4 4" />
          <path d="M20 7H4" />
          <path d="m8 21-4-4 4-4" />
          <path d="M4 17h16" />
        }
        @case ('extrato') {
          <path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z" />
          <path d="M14 2v4a2 2 0 0 0 2 2h4" />
          <path d="M10 9H8" />
          <path d="M16 13H8" />
          <path d="M16 17H8" />
        }
        @case ('sair') {
          <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
          <path d="m16 17 5-5-5-5" />
          <path d="M21 12H9" />
        }
        @case ('escudo') {
          <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
          <path d="m9 12 2 2 4-4" />
        }
        @case ('olho') {
          <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
          <circle cx="12" cy="12" r="3" />
        }
        @case ('olho-riscado') {
          <path d="M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0" />
          <circle cx="12" cy="12" r="3" />
          <path d="m2 2 20 20" />
        }
        @case ('menu') {
          <path d="M4 6h16M4 12h16M4 18h16" />
        }
        @case ('fechar') {
          <path d="M18 6 6 18M6 6l12 12" />
        }
        @case ('seta-direita') {
          <path d="m9 18 6-6-6-6" />
        }
      }
    </svg>
  `,
})
export class IconeComponent {
  readonly nome = input.required<NomeIcone>();
  readonly tamanho = input(20);
}
