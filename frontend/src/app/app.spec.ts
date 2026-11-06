import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { appConfig } from './app.config';
import { App } from './app';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: appConfig.providers }).compileComponents();
  });

  it('cria o aplicativo', () => {
    expect(TestBed.createComponent(App).componentInstance).toBeTruthy();
  });

  it('abre a visão geral por padrão, dentro do shell', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);
    await router.navigateByUrl('/');
    await fixture.whenStable();
    expect(router.url).toBe('/visao-geral');
    expect((fixture.nativeElement as HTMLElement).querySelector('aside')).not.toBeNull();
  });

  it('a rota de login fica fora do shell', async () => {
    const fixture = TestBed.createComponent(App);
    await TestBed.inject(Router).navigateByUrl('/login');
    await fixture.whenStable();
    expect((fixture.nativeElement as HTMLElement).querySelector('aside')).toBeNull();
  });
});
