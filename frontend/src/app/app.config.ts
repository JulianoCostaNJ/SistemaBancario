import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { BancoApi } from './core/api/banco-api';
import { BancoApiMock } from './core/api/banco-api.mock';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Único ponto onde se escolhe a implementação da API: trocar por HttpBancoApi na integração.
    { provide: BancoApi, useClass: BancoApiMock },
  ],
};
