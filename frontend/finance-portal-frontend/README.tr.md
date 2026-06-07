# Finans Portalı — Frontend

[English](README.en.md) · **Türkçe**

React 19 + Vite üzerine kurulu, **özellik tabanlı (feature-based)** tek sayfa uygulaması (SPA).

> Bu döküman frontend'e özel teknik detayları içerir. Projenin geneli, kurulum ve çalıştırma için [ana README](../../README.md)'ye bakınız.

## Mimari Yaklaşım

Uygulama, teknik türe göre değil, işlevsel **özellik alanlarına (feature)** göre organize edilmiştir. Tüm veri backend'den `/api/v1/*` ile alınır.

```
src/
├── features/        # Özellik alanları (9 alan, 47 sayfa)
│   ├── dashboard/   # Özelleştirilebilir kontrol paneli (sürükle-bırak)
│   ├── market/      # 10 varlık türü (hisse, kripto, fx, fon, tahvil, viop, emtia, altın, endeks, ekonomi)
│   ├── portfolio/   # Portföy, holdings, AI analiz
│   ├── alarms/      # Alarm yönetimi
│   ├── news/        # Haber listesi/detay
│   ├── notifications/ # Bildirim merkezi
│   ├── profile/     # Profil ve tercihler
│   ├── admin/       # Yönetim panelleri
│   └── auth/        # Giriş, kayıt, e-posta doğrulama
├── app/             # Uygulama kabuğu (AppLayout, Header, Footer, ProtectedRoute)
├── components/      # Paylaşılan UI bileşenleri
├── context/         # Global durum (7 React Context)
├── api/             # Backend REST çağrı katmanı (Axios)
├── router/          # Sayfa yönlendirme (ProtectedRoute / AdminRoute)
├── hooks/           # Özel React kancaları
├── i18n/            # Çok dilli metin (TR / EN, 14 namespace)
├── lib/             # HTTP istemcisi (token interceptor)
└── utils/           # Yardımcı fonksiyonlar, biçimlendiriciler
```

## Başlıca Özellikler

- **3 erişim katmanı** — Genel (misafir), Korumalı (kayıtlı kullanıcı), Admin — `ProtectedRoute` / `AdminRoute` ile.
- **Durum yönetimi** — 7 React Context (kimlik, tema, dil, tercihler, izleme listesi, bildirim, onay). Redux yoktur.
- **Kimlik** — Keycloak OIDC (PKCE / S256), proaktif + tek-uçuşan (single-flight) token yenileme.
- **Grafikler** — klinecharts (detay mum + MA/RSI/MACD/Bollinger + çizim), ECharts (kıyaslama), Recharts (analiz / dağılım).
- **Tema & dil** — Açık / koyu tema (CSS değişkenleri, FOUC önleme), TR / EN i18n, çoklu para birimi gösterimi.
- **Dışa aktarma** — Excel (xlsx), PDF (jsPDF), grafik görseli (PNG / html-to-image).
- **Cihazlar arası senkron** — Kullanıcı tercihleri (kontrol paneli düzeni, tema, dil, grafik çizimleri) sunucuda saklanır.

## Teknolojiler

React 19, Vite, React Router 7, Tailwind CSS, Axios; klinecharts, ECharts, Recharts; react-grid-layout (sürükle-bırak); html-to-image, jsPDF, xlsx (dışa aktarma); lucide-react (ikonlar).

## Yerel Geliştirme

> Önkoşul: **Node.js 20+**. Backend'in çalışıyor olması gerekir (Docker ile veya ayrıca). Tüm yığın için [ana README](../../README.md)'ye bakınız.

```bash
# Bağımlılıkları kur
npm install

# Geliştirme sunucusu (HMR) — http://localhost:5173
npm run dev

# Üretim derlemesi — dist/ klasörüne
npm run build

# Derlemeyi önizle
npm run preview
```

## Test ve Kalite

```bash
npm run test            # Vitest testleri (tek seferlik)
npm run test:watch      # izleme modunda
npm run test:coverage   # kapsama raporu (v8)
npm run lint            # ESLint
```

- **~2.500 test** (Vitest + React Testing Library + jsdom).

## Yapılandırma

Ortam değişkenleri `.env` dosyasında (`.env.example`'dan kopyalanır). Önemli değişkenler:

| Değişken | Amaç |
|---|---|
| `VITE_KEYCLOAK_URL` | Keycloak sunucu adresi (OIDC) |
| `VITE_KEYCLOAK_REALM` | Keycloak realm adı |

> Bu değerler üretim derlemesinde imaja gömülür (build-arg). Verilmezse koddaki yerel varsayılanlar kullanılır.

> Ayrıntılı tasarım (bileşen mimarisi, durum yönetimi, grafik stratejisi, i18n) için **Teknik Analiz Dökümanına** bakınız.
