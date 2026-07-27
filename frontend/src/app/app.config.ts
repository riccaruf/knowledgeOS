import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  provideKeycloak,
  includeBearerTokenInterceptor,
  INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
  IncludeBearerTokenCondition,
  createInterceptorCondition,
  withAutoRefreshToken,
  AutoRefreshTokenService,
  UserActivityService,
} from 'keycloak-angular';

import { routes } from './app.routes';
import { appConfig as knowledgeOsConfig } from './core/app-config';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(withInterceptors([includeBearerTokenInterceptor])),
    provideKeycloak({
      config: {
        url: knowledgeOsConfig.keycloakUrl,
        realm: knowledgeOsConfig.keycloakRealm,
        clientId: knowledgeOsConfig.keycloakClientId,
      },
      initOptions: {
        onLoad: 'login-required',
      },
      features: [withAutoRefreshToken({ onInactivityTimeout: 'logout', sessionTimeout: 300000 })],
      providers: [AutoRefreshTokenService, UserActivityService],
    }),
    {
      provide: INCLUDE_BEARER_TOKEN_INTERCEPTOR_CONFIG,
      useValue: [
        createInterceptorCondition<IncludeBearerTokenCondition>({
          urlPattern: new RegExp(`^${knowledgeOsConfig.apiBaseUrl}(/.*)?$`),
        }),
      ],
    },
  ],
};
