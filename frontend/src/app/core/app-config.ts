/**
 * Configurazione runtime del frontend per lo sviluppo locale (docker-compose).
 * In un deployment reale questi valori andrebbero esternalizzati (es. file di
 * configurazione servito insieme ai file statici), non richiesto per l'MVP.
 */
export const appConfig = {
  keycloakUrl: 'http://localhost:8081',
  keycloakRealm: 'knowledgeos',
  keycloakClientId: 'knowledgeos-frontend',
  apiBaseUrl: 'http://localhost:8080',
};
