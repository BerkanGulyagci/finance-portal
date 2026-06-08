# Finans Portalı — Backend

[English](README.md) · **Türkçe**

Java 21 + Spring Boot 3.2.1 üzerine kurulu, **modüler monolit + Clean Architecture** mimarisinde REST API.

> Bu döküman backend'e özel teknik detayları içerir. Projenin geneli, kurulum ve çalıştırma için [ana README](../../README.md)'ye bakınız.

## Mimari Yaklaşım

Backend, **modüler monolit** olarak tasarlanmıştır: tek bir dağıtılabilir uygulama içinde, işlevsel alanlara (domain) göre net biçimde ayrılmış 12 modül. Her modül, **Clean Architecture** katmanlarına bölünmüştür.

```
com/finance/portal/
├── market/          # Piyasa verileri (hisse, kripto, döviz, fon, tahvil, VİOP, emtia, endeks, ekonomi)
├── portfolio/       # Portföy, işlem, izleme listesi, değerleme, what-if, AI analiz
├── alarm/           # Fiyat / değişim / hacim alarmları
├── notification/    # Uygulama içi bildirim + e-posta
├── news/            # Çok kaynaklı haber toplama + kişiselleştirme
├── assistant/       # Yapay zekâ sohbet asistanı (tool-calling)
├── newsletter/      # Bülten aboneliği + dijest
├── support/         # Destek talepleri
├── preferences/     # Kullanıcı tercihleri (cihazlar arası senkron)
├── admin/           # Kullanıcı yönetimi, ban (Keycloak)
├── auth/            # Kimlik doğrulama yardımcıları, kayıt
└── common/          # Çapraz kesen: güvenlik, loglama, önbellek, hata, config
```

Her domain modülü 4 katmandan oluşur:

| Katman | İçerik | Bağımlılık |
|---|---|---|
| `presentation` | REST denetleyiciler (`controller`), DTO'lar | Application'a |
| `application` | İş akışı servisleri, `port` arayüzleri | Domain'e |
| `domain` | İş varlıkları (entity), kurallar | (en içte — bağımsız) |
| `infrastructure` | Port gerçekleştirimleri: adapter, repository, dış servis istemcisi | Application + Domain'e |

**Bağımlılık kuralı:** Bağımlılıklar daima dıştan içe akar. Dış bağımlılıklar (veritabanı, dış API) `port` arayüzleriyle soyutlanır; infrastructure bunları gerçekleştirir (Dependency Inversion).

## Başlıca Bileşenler

- **36 REST denetleyici, 124 uç nokta** — tümü `/api/v1/**` altında, `ApiResponse<T>` zarfıyla.
- **31 zamanlanmış görev** (22 sınıfa dağılmış) — alarm değerlendirme, önbellek ısıtma, vade kapatma, bülten dijesti (ShedLock ile dağıtık kilit).
- **29 dış servis istemcisi** — port/adapter ile soyutlanmış (Yahoo, Binance, TCMB, TEFAS, İş Yatırım vb.).
- **Dayanıklılık:** Last Known Good (LKG) + Resilience4j (retry / circuit breaker) + 50+ Redis önbellek ad alanı.
- **10 JPA entity, 17 Flyway migration** (`V1`–`V12`, sonrası tarih-bazlı `V20260525_01`+).

## Teknolojiler

Spring Boot (Web, Security / OAuth2 Resource Server, Data JPA, Data Redis, Kafka, Mail, Cache, Validation, Actuator), Flyway, Lombok, Resilience4j, ShedLock, OpenTelemetry, Micrometer / Prometheus, log4j2 (JSON), springdoc-openapi (Swagger), Jsoup.

## Yerel Çalıştırma (Docker'sız)

> Tüm yığını Docker ile çalıştırmak için [ana README](../../README.md)'deki kurulum adımlarını izleyin. Aşağıdaki yalnızca backend'i tek başına çalıştırmak içindir (PostgreSQL, Redis, Keycloak'ın ayrıca ayakta olması gerekir).

```bash
# Bağımlılıkları indir + derle
./mvnw clean package

# Çalıştır (varsayılan: localhost'taki PostgreSQL / Redis / Keycloak)
./mvnw spring-boot:run

# Veya jar olarak
java -jar target/*.jar
```

Uygulama `http://localhost:8080` üzerinde başlar. Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Test

```bash
./mvnw test          # birim + entegrasyon testleri (Testcontainers) + JaCoCo
./mvnw verify        # CI'da çalışan tam doğrulama
```

- **~2.700 test** (JUnit 5, Spring Boot Test, Testcontainers, WireMock).
- Kapsama raporu: `target/site/jacoco/index.html`.

## Veritabanı Migration

Şema, **Flyway** ile yönetilir (`src/main/resources/db/migration/`). Yeni değişiklik için yeni bir migration eklenir (tarih-bazlı adlandırma, eşzamanlı çalışmada sürüm çakışmasını önler):

```
V20260615_01__yeni_degisiklik.sql
```

Migration'lar uygulama açılışında otomatik çalışır. Mevcut şema 10 tablodan oluşur (portfolio, alarm, notification, watchlist_item vb.).

## Yapılandırma

Ayarlar `application.yml` (+ profil dosyaları) ile yönetilir; ortam değişkenleriyle (`${ENV_VAR:varsayılan}`) geçersiz kılınabilir. Hassas değerler `.env.local` (gitignore) içindedir. Üretim profili (`prod`), kritik sır eksikse başlatmayı durdurur (fail-loud).

> Ayrıntılı tasarım (Clean Architecture, port/adapter, dayanıklılık, gözlemlenebilirlik) için **Teknik Analiz Dökümanına** bakınız.
