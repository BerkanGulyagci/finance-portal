import axios from 'axios';

const TOKEN_KEY = 'auth_token';

const client = axios.create({
  baseURL: 'http://localhost:8080',
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token && !isTokenExpired(token)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true; // malformed token → treat as expired
  }
}

export default client;
