import axios from 'axios';

const KEYCLOAK_URL = 'http://localhost:8081';
const REALM = 'finance-portal';
const CLIENT_ID = 'finance-portal-api';

const TOKEN_ENDPOINT = `${KEYCLOAK_URL}/realms/${REALM}/protocol/openid-connect/token`;

/**
 * Authenticates against Keycloak using Resource Owner Password Credentials flow.
 * @returns {{ access_token, refresh_token, token_type, expires_in }}
 * @throws Error with user-friendly message on failure
 */
export async function loginRequest(username, password) {
  const body = new URLSearchParams({
    grant_type: 'password',
    client_id: CLIENT_ID,
    username,
    password,
  });

  try {
    const { data } = await axios.post(TOKEN_ENDPOINT, body.toString(), {
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    });

    return {
      access_token: data.access_token,
      refresh_token: data.refresh_token ?? null,
      token_type: data.token_type,
      expires_in: data.expires_in,
    };
  } catch (err) {
    if (!err.response) {
      throw new Error('Unable to reach authentication server. Check your connection.');
    }
    const status = err.response.status;
    const errorCode = err.response.data?.error;
    if (status === 401 || errorCode === 'invalid_grant') {
      throw new Error('Invalid username or password.');
    }
    throw new Error(`Authentication failed (${status}).`);
  }
}
