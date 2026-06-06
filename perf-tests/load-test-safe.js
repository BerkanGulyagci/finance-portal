// GÜVENLİ yük testi — SADECE cache'li/iç endpoint'ler (dış API'ye GİTMEZ).
// Rate-limit riski YOK. Kademeli 300→500→1000 kullanıcı, gerçek sınırı bul.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('custom_errors');
const apiDuration = new Trend('api_duration');

export const options = {
  stages: [
    { duration: '1m', target: 300 },   // 300 kullanıcıya çık
    { duration: '2m', target: 500 },   // 500'e çık
    { duration: '2m', target: 1000 },  // 1000'e çık (gerçek sınır)
    { duration: '1m', target: 0 },     // soğuma
  ],
  thresholds: {
    http_req_duration: ['p(95)<8000'],
    custom_errors: ['rate<0.2'],
  },
};

const BASE = 'http://backend:8080';

// SADECE güvenli (cache'li/iç) endpoint'ler — dış API çağıran bonds/global YOK
const endpoints = [
  '/actuator/health/readiness',
  '/api/v1/market/bonds/evds',
  '/api/v1/news',
];

export default function () {
  const url = BASE + endpoints[Math.floor(Math.random() * endpoints.length)];
  const res = http.get(url, { timeout: '15s' });
  apiDuration.add(res.timings.duration);
  const ok = res.status === 200;
  errorRate.add(!ok);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(Math.random() * 0.5 + 0.2);
}
