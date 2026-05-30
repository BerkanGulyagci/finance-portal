import axios from 'axios';

const TOKEN_KEY = 'auth_token';

const client = axios.create({
  // Boş baseURL → relative path kullanır.
  // Docker'da Nginx /api/* isteklerini backend:8080'e proxy'ler.
  // Local dev'de vite.config.js proxy'si devreye girer.
  baseURL: '',
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

function forceLogout(redirectPath = '/login') {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem('id_token');
  window.location.href = redirectPath;
}

// 401 / banlı hesap (403) → oturumu sonlandır
client.interceptors.response.use(
  response => response,
  error => {
    const status = error.response?.status;
    const message = error.response?.data?.message || '';

    if (status === 401) {
      forceLogout('/login');
    } else if (status === 403 && message.startsWith('ACCOUNT_DISABLED')) {
      forceLogout('/login?banned=1');
    } else if (status === 403 && message.startsWith('EMAIL_NOT_VERIFIED')) {
      window.location.href = '/verify-email';
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
