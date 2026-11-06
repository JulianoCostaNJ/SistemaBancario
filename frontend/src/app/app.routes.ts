import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell.component';

/** Tela provisória: cada rota troca o `loadComponent` pela tela definitiva na sua etapa. */
const emConstrucao = () =>
  import('./features/em-construcao/em-construcao.component').then((m) => m.EmConstrucaoComponent);

export const routes: Routes = [
  { path: 'login', loadComponent: emConstrucao, data: { titulo: 'Entrar', etapa: 2 } },
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'visao-geral' },
      { path: 'visao-geral', loadComponent: emConstrucao, data: { titulo: 'Visão geral', etapa: 2 } },
      { path: 'deposito', loadComponent: emConstrucao, data: { titulo: 'Depósito', etapa: 3 } },
      { path: 'saque', loadComponent: emConstrucao, data: { titulo: 'Saque', etapa: 3 } },
      { path: 'transferencia', loadComponent: emConstrucao, data: { titulo: 'Transferência', etapa: 4 } },
      { path: 'extrato', loadComponent: emConstrucao, data: { titulo: 'Extrato', etapa: 5 } },
    ],
  },
  { path: '**', redirectTo: '' },
];
