import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

/**
 * Tela provisória das rotas que ainda não foram entregues. Lê o título e a etapa do `data` da
 * rota, para que cada página seja trocada pela definitiva sem mexer em `app.routes.ts` além do
 * `loadComponent`.
 */
@Component({
  selector: 'app-em-construcao',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'block animate-entrada' },
  template: `
    <section class="rounded-2xl border border-borda bg-white p-8 shadow-sm">
      <h1 class="text-2xl font-semibold text-tinta">{{ titulo }}</h1>
      <p class="mt-2 max-w-prose text-sm text-tinta-suave">
        Esta tela será entregue na etapa {{ etapa }} do plano, seguindo o protótipo.
      </p>
      <div class="mt-6 space-y-3" aria-hidden="true">
        <div class="h-4 w-2/3 animate-pulse rounded bg-fundo"></div>
        <div class="h-4 w-1/2 animate-pulse rounded bg-fundo"></div>
        <div class="h-24 animate-pulse rounded-xl bg-fundo"></div>
      </div>
    </section>
  `,
})
export class EmConstrucaoComponent {
  private readonly rota = inject(ActivatedRoute);
  protected readonly titulo: string = this.rota.snapshot.data['titulo'] ?? '';
  protected readonly etapa: number = this.rota.snapshot.data['etapa'] ?? 0;
}
