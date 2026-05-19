const keycloakUrl = (import.meta.env.VITE_KEYCLOAK_URL || 'http://localhost:8081').replace(/\/$/, '');
const realm = import.meta.env.VITE_KEYCLOAK_REALM || 'finance-portal';

const accountPath = `${keycloakUrl}/realms/${realm}/account`;

/** Keycloak Account Console ana sayfa */
export function getAccountConsoleUrl() {
  return `${accountPath}/`;
}

/** Account security → Signing in (şifre değiştirme) — Keycloak 26 */
export function getAccountSigningInUrl() {
  return `${accountPath}/account-security/signing-in`;
}

export const keycloakAccountUrls = {
  home: getAccountConsoleUrl(),
  changePassword: getAccountSigningInUrl(),
};
