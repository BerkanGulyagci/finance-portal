/**
 * JWT payload decode (UI only — signature doğrulaması backend'de yapılır).
 */
export function parseJwtPayload(token) {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    if (!payload) return null;
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
    return JSON.parse(atob(padded));
  } catch {
    return null;
  }
}

export function isTokenValid(token) {
  const claims = parseJwtPayload(token);
  if (!claims?.exp) return false;
  return claims.exp * 1000 > Date.now();
}

export function extractAuthClaims(token) {
  const claims = parseJwtPayload(token);
  if (!claims) {
    return {
      userId: null,
      username: null,
      email: null,
      emailVerified: false,
      roles: [],
      isAdmin: false,
    };
  }

  const realmAccess = claims.realm_access;
  const roles = Array.isArray(realmAccess?.roles) ? realmAccess.roles : [];

  const emailVerified = claims.email_verified === true
    || claims.email_verified === 'true';

  return {
    userId: claims.sub ?? null,
    username: claims.preferred_username ?? claims.username ?? null,
    email: claims.email ?? null,
    emailVerified,
    roles,
    isAdmin: roles.includes('ADMIN'),
  };
}
