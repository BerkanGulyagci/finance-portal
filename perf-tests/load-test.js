// k6 yük testi — backend kapasitesini ÖLÇER + HPA'yı tetikler.
// Soru: "5-6 pod birçok eşzamanlı kullanıcıya yeter mi?"
// Kademeli ramp → knee-point (latency/error patlama noktası) = gerçek kapasite.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('custom_errors');
const apiDuration = new Trend('api_duration');

export const options = {
  stages: [
    { duration: '1m', target: 30 },   // ısınma
    { duration: '2m', target: 60 },   // orta yük → HPA tetikle
    { duration: '2m', target: 100 },  // yüksek yük → kapasite sınırı
    { duration: '1m', target: 0 },    // soğuma → scale-down
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],
    custom_errors: ['rate<0.1'],       // <%10 hata bekleriz
  },
};

const BASE = 'http://backend:8080';

// Gerçekçi karışım: çoğu cache-sıcak hafif liste, bir kısmı ağır
const endpoints = [
  '/api/v1/market/bonds/global',   // liste (cache'li)
  '/api/v1/market/bonds/evds',     // EVDS (cache'li, ağır kaynak)
  '/actuator/health/readiness',    // hafif (DB+redis check)
];

export default function () {
  const url = BASE + endpoints[Math.floor(Math.random() * endpoints.length)];
  const res = http.get(url, { timeout: '15s' });
  apiDuration.add(res.timings.duration);
  const ok = res.status === 200;
  errorRate.add(!ok);
  check(res, { 'status 200': (r) => r.status === 200 });
  sleep(Math.random() * 1 + 0.3);   // 0.3-1.3s düşünme süresi
}
