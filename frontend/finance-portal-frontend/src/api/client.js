import axios from 'axios';

const TOKEN_KEY = 'auth_token';

const client = axios.create({
  baseURL: 'http://localhost:8080',
});

client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    if (isTokenExpired(token)) {
      // Token süresi dolmuş — temizle ve login'e yönlendir
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('id_token');
      window.location.href = '/login';
      return Promise.reject(new Error('Token expired'));
    }
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 401 response gelirse de login'e yönlendir
client.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem('id_token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

function isTokenExpired(token) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

export default client;
