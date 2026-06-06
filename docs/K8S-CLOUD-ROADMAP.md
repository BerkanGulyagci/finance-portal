# Portiva — GKE / Kubernetes Yol Haritası

> **Amaç:** Local Docker Compose ile çalışan Portiva'yı Google Cloud (GKE) üzerinde
> Kubernetes ile çalıştırmak; yatay ölçekleme (HPA/autoscaling), yük testi (k6),
> connection pooling ve CI/CD (otomatik deploy) eklemek.
>
> **🛡️ ALTIN KURAL: Local projeye ASLA dokunulmaz.** `docker-compose.yml`, mevcut
> Dockerfile'lar, kaynak kod — hepsi olduğu gibi kalır ve local'de çalışmaya devam eder.
> Tüm K8s işi AYRI dosyalarda (`k8s/` klasörü) ve AYRI branch'te yapılır.

---

## 📍 Mevcut Durum (başlangıç noktası)

- **kubectl** ✅ kurulu (v1.34.1)
- **docker** ✅ kurulu
- **winget** ✅ var (gcloud kurulumu için)
- **gcloud CLI** ❌ kurulu değil → Faz 1'de kurulacak
- **k8s/ klasörü** yok → sıfırdan
- **CI** var (`.github/workflows/ci.yml`), **CD** yok → Faz 6'da eklenecek
- **16 servis** docker-compose'da, **6 named volume**

## ✅ ALINAN KARARLAR (kullanıcı onaylı)

| Konu | Karar |
|---|---|
| **Platform** | GKE (Google Kubernetes Engine) — direkt gerçek cloud |
| **Credit** | $300 free credit ile başla |
| **Branch** | `main` (K8s dosyaları yeni, mevcut hiçbir şeyi bozmaz) |
| **Compose** | Local'de aynen kalır (DOKUNULMAZ); cloud için AYRI güçlü Secret'lar |
| **Mimari** | Tek node pool + workload separation (3 ayrı pool DEĞİL — daha ucuz/doğru) |
| **Kapsam** | **16 servisin TAMAMI** (tam kopya) |
| **Cluster boyutu** | ~10-14GB RAM (3-4 node e2-medium veya 2× e2-standard-4) |
| **Tahmini maliyet** | günlük ~$8-15, $300 sürekli açıkta ~20-30 gün |

## 💰 MALİYET KONTROLÜ (her adımda hatırlatılacak)

> **🔑 Altın kural: Test yapmadığında cluster'ı DURDUR.** Yoksa credit hızla biter.

Maliyet düşürme yöntemleri (öğretilecek):
1. **Node pool'u 0'a indir** (`gcloud container clusters resize ... --num-nodes 0`) → compute ücreti durur, cluster tanımı kalır
2. **Cluster'ı tamamen sil** (`gcloud container clusters delete`) → her şey durur (manifest'ler git'te kalır, tekrar deploy edilebilir)
3. **GKE Autopilot** opsiyonu: sadece çalışan pod'lar için öde (idle node ücreti yok)
4. Her oturum sonunda: "cluster'ı durdur/sil" hatırlatması

---

## 🏗️ Senin Mimari Planın (3 Node) — K8s karşılığı

Senin "3 sunucu" fikrin doğru. K8s terminolojisinde:

| Senin terimin | K8s karşılığı |
|---|---|
| Sunucu / makine | **Node** |
| "Hangi konteyner nerede" | **nodeSelector / affinity / taint-toleration** |
| Fiziksel disk | **PersistentVolume + PVC** |
| Dışa açık/kapalı | **Service tipi** (ClusterIP=iç, LoadBalancer/Ingress=dış) |

### Node grupları (senin planın)

**Node Grubu 1 — Veri Katmanı (dışa kapalı, kalıcı disk)**
- `postgres`, `apacheds` (LDAP), `kafka`, `redis`
- K8s tipi: **StatefulSet + PVC** (veri uçmaz)
- Erişim: sadece cluster içi (ClusterIP)

**Node Grubu 2 — Uygulama Katmanı (dışa açık, ÖLÇEKLENEN)**
- `backend` (Spring Boot), `frontend` (nginx), `keycloak`
- K8s tipi: **Deployment** (backend/frontend), Keycloak StatefulSet
- Erişim: **Ingress** ile dış dünya
- **HPA buraya takılır** (asıl ölçeklenen: backend + frontend)

**Node Grubu 3 — İzleme Katmanı (internete tamamen kapalı)**
- `grafana`, `prometheus`, `tempo`, `otel-collector`, `opensearch`,
  `opensearch-dashboards`, `log-consumer`, `sonarqube`
- Erişim: `kubectl port-forward` ile (SSH tünelinden bile basit — sadece sen erişirsin)

> **Not:** GKE'de "3 ayrı node" yerine genelde **tek node pool + workload separation**
> (affinity ile) yapılır — daha ucuz. İstersen gerçek 3 node pool da yaparız (Faz 4'te karar).

---

## 🗺️ FAZLAR (sırayla, her biri ayrı oturum olabilir)

### FAZ 0 — Hazırlık & Güvenli temizlik (local'e dokunmadan)
- [ ] Yeni branch: `feature/k8s-gke`
- [ ] `k8s/` klasör yapısı oluştur (boş iskelet)
- [ ] Compose'daki hardcoded şifreleri env-referansına çevir (K8s Secret'a geçişi kolaylaştırır)
      — **dikkatli, test ederek; local çalışmaya devam etmeli**
- **Çıktı:** Temiz başlangıç, local hâlâ çalışıyor

### FAZ 1 — Google Cloud kurulum & GKE cluster
- [ ] gcloud CLI kurulumu + Google hesabı/proje + faturalandırma (free credit)
- [ ] **Maliyet uyarısı:** GKE ücretli — küçük cluster ~aylık tahmin çıkaracağız;
      free-tier/credit kullanımı + iş bitince cluster'ı silme stratejisi
- [ ] GKE cluster oluştur (küçük: 2-3 node, e2-small/medium)
- [ ] `kubectl`'i GKE'ye bağla (`gcloud container clusters get-credentials`)
- **Çıktı:** Çalışan boş GKE cluster

### FAZ 2 — Image'ları Container Registry'ye gönder
- [ ] Google Artifact Registry oluştur
- [ ] Mevcut Dockerfile'larla image build + push (backend/frontend/log-consumer)
- [ ] Hazır image'lar (postgres/redis/kafka vs) zaten public registry'de — direkt kullanılır
- **Çıktı:** Image'lar cloud registry'de hazır

### FAZ 3 — K8s Manifest'leri (asıl iş — local'e dokunmaz)
`k8s/` altında YAML manifest'leri:
- [ ] **Namespace** (portiva)
- [ ] **ConfigMap** (uygulama ayarları) + **Secret** (şifreler — compose'dan map)
- [ ] **Veri katmanı:** postgres/redis/kafka/apacheds → StatefulSet + PVC + ClusterIP Service
- [ ] **Uygulama katmanı:** backend/frontend/keycloak → Deployment + Service
- [ ] **İzleme katmanı:** grafana/prometheus/... → Deployment + ClusterIP (dışa kapalı)
- [ ] **Ingress** (backend API + frontend dış erişim)
- [ ] **Readiness/Liveness probe** (compose healthcheck'in K8s karşılığı — backend actuator/health)
- **Çıktı:** Sistem GKE'de ayakta, dışarıdan erişilebilir

### FAZ 4 — Yatay Ölçekleme (HPA / Autoscaling) — SENİN ASIL HEDEFİN
- [ ] **HPA (Horizontal Pod Autoscaler):** backend + frontend için
      — "CPU %70'i geçince yeni pod aç" gibi
- [ ] **Cluster Autoscaler:** node'lar dolunca yeni node ekle (GKE otomatik)
- [ ] **Connection pooling:** backend DB bağlantı havuzu (HikariCP zaten var — pod
      sayısı × pool boyutu DB limitini aşmasın diye ayar + gerekirse PgBouncer)
- **Çıktı:** Yük artınca sistem otomatik büyüyor

### FAZ 5 — Yük Testi (k6) — ARTIK ANLAMLI
- [ ] k6 script'leri (ana endpoint'lere yük)
- [ ] Test: yük bindir → HPA yeni pod açıyor mu, kapasite ne, response time
- [ ] Grafana'da gerçek-zamanlı izle (pod sayısı, CPU, latency)
- **Çıktı:** "Sistem X eşzamanlı kullanıcı kaldırıyor + otomatik ölçekleniyor" kanıtı

### FAZ 6 — CI/CD (Continuous Deployment)
- [ ] GitHub Actions'a CD pipeline: push → build image → push registry → GKE'ye deploy
- [ ] Otomatik rollout + rollback
- **Çıktı:** Kod push'layınca otomatik cloud'a deploy

### FAZ 7 — Güvenlik & Maliyet sertleştirme (opsiyonel)
- [ ] İzleme katmanı tamamen iç ağda (NetworkPolicy)
- [ ] Secret'lar Google Secret Manager'da
- [ ] Maliyet izleme + cluster'ı kullanmadığında durdurma/silme

---

## 💰 Maliyet Notu (önemli)
GKE **ücretli**. Yaklaşımımız:
- Küçük cluster (öğrenme için yeterli) + Google **free credit** ($300 başlangıç)
- İş bitince / öğrenince **cluster'ı sil** (durunca ücret kesilir)
- Her faz başında tahmini maliyet konuşulur

---

## 🎓 Öğrenme Notu
Kullanıcı K8s'e yeni → her fazda:
1. **Önce ne yapacağımızı + neden açıklarım** (kavram)
2. Adımı uygularız
3. **Sonucu birlikte doğrularız** (çalışıyor mu)
Acele yok — her şey anlaşılarak ilerler. Local proje hiçbir zaman riske atılmaz.

---

## ✅ İlerleme Takibi
- [x] FAZ 0 — Hazırlık (gcloud kuruldu, hesap girişi, proje set, API'ler açık)
  - Hesap: bgulyaci@gmail.com
  - Proje ID: project-f3ab8cbf-895a-47c2-91d
  - Credit: ₺13,769 (~$300), 90 gün (5 Eylül 2026'ya kadar)
  - Açık API'ler: container, artifactregistry, compute
- [x] FAZ 1 — GKE cluster ✅ TAMAMLANDI
  - Cluster: portiva-cluster (Autopilot)
  - Bölge: europe-west1
  - Durum: RUNNING | K8s 1.35.3 | Master IP 34.62.153.220
  - kubectl bağlı (gke-gcloud-auth-plugin kuruldu)
  - NOT: Autopilot'ta `kubectl get nodes` "No resources" döner (normal — node'lar pod gelince otomatik)
  - ⚠️ Her yeni terminal'de: `$env:PATH` + `$env:USE_GKE_GCLOUD_AUTH_PLUGIN="True"` gerekebilir
- [x] FAZ 2 — Registry (image push) ✅
  - Artifact Registry: portiva-images (europe-west1)
  - Push: backend:v1, frontend:v1, log-consumer:v1
  - Adres: europe-west1-docker.pkg.dev/project-f3ab8cbf-895a-47c2-91d/portiva-images
- [x] FAZ 3 — Manifest'ler + ÇEKİRDEK DEPLOY ✅ (k8s/ klasörü)
  - **Çekirdek 7 servis GKE'de RUNNING:** postgres, redis, backend, frontend, kafka, keycloak, apacheds
  - Namespace: portiva | Secret: backend-secrets(26)+db-secrets | ConfigMap: otel/tempo/prometheus/postgres-init
  - Çözülen 6 K8s sorunu:
    1. Image auth 403 → node SA'ya `roles/artifactregistry.reader` verildi
    2. GCE kota aşıldı → izleme katmanı GEÇİCİ kaldırıldı (k8s/03-monitoring/ duruyor, kota gelince geri)
    3. Kafka AccessDenied → securityContext fsGroup:1000
    4. Kafka lost+found → KAFKA_LOG_DIRS=/var/lib/kafka/data/logs (alt-klasör)
    5. Kafka KRaft timeout → headless Service (clusterIP:None) + pod DNS (kafka-0.kafka...)
    6. Keycloak crash → KC_HEALTH_ENABLED=true + probe port 8080→9000 (management)
  - replicas: backend/frontend 1'e indirildi (kota; HPA ekleyince ölçeklenecek)
  - ⏸️ KALDI: izleme katmanı (kota), Keycloak realm-import (ConfigMap), Ingress, HPA, k6
- [x] FAZ 4 — Ingress + HPA + k6 ✅ TAMAMLANDI
  - **Ingress dış IP: 8.233.66.172** (site canlı, frontend + /api → backend)
  - **502 KÖK SEBEP çözüldü:** MailHealthIndicator (Gmail SMTP "Too many login attempts")
    aggregate `/actuator/health`'i 503 yapıyordu → pod READY olamıyor → endpoint boş → 502.
    Çözüm: K8s probe path'leri + GCLB health check `/actuator/health/readiness`'e (mail içermeyen
    grup — application.yml'de tanımlı) yönlendirildi + BackendConfig (k8s/02-app/backend-backendconfig.yaml).
  - **HPA çalışıyor:** backend-hpa, CPU %50 hedef, min 1 / max 6. k6 yükünde 2→6 pod otomatik.
  - **k6 KAPASİTE TESTİ (kanıt):** 100 eşzamanlı kullanıcı, 22,780 istek, **%0 hata**, p95=58ms.
    Yük altında HİÇ 502 yok (her ölçümde OK 200). 6 pod bu yükü rahat karşıladı.
  - Haberler/news (/api/v1/news) cloud'da ÇALIŞIYOR (backend sağlıklı olunca geldi).
- [x] FAZ 5 — İzleme katmanı DEPLOY EDİLDİ ✅ (grafana/prometheus/tempo/otel/log-consumer/opensearch
      cluster'da Running; fsGroup + memory + opensearch-dashboards kaldırma ile sığdırıldı).
      Grafana'da 4 dashboard provision edildi (cloud canlı veri). Erişim: port-forward (localhost:3001).
      Keycloak realm-import de tamam (ConfigMap mount). Keycloak admin: dinamik hostname + port-forward 8082.
- [ ] FAZ 6 — CI/CD (push → otomatik deploy)
- [ ] FAZ 7 — Sertleştirme (secret/non-root/maliyet)

## ⚠️ AKTİF MALİYET — cluster RUNNING (credit yakıyor)
Test bitince cluster'ı durdur/sil. Geri kurmak için:
```
kubectl apply -f k8s/00-base/ k8s/01-data/ k8s/02-app/
```
(izleme katmanı k8s/03-monitoring/ kota gelince; replicas backend için HPA yönetir)

## 🔑 Faydalı gcloud komutları (maliyet kontrolü)
```bash
# Cluster'ı DURDUR (node 0 → compute ücreti durur, cluster kalır)
gcloud container clusters resize CLUSTER_ADI --num-nodes=0 --zone=ZONE

# Cluster'ı tekrar BAŞLAT
gcloud container clusters resize CLUSTER_ADI --num-nodes=2 --zone=ZONE

# Cluster'ı tamamen SİL (her şey durur, manifest'ler git'te kalır)
gcloud container clusters delete CLUSTER_ADI --zone=ZONE

# Mevcut cluster'ları listele
gcloud container clusters list
```
