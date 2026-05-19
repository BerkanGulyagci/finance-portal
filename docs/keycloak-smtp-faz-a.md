# Faz A — Keycloak gerçek SMTP

Bu faz yalnızca Keycloak’un gerçek SMTP ile mail gönderebilmesini hedefler. Forgot Password ve Verify Email (Faz B/C) bu altyapıya bağlıdır.

## Önemli: Keycloak 26 ve environment variables

**Keycloak 26, realm SMTP ayarlarını (`host`, `password`, vb.) environment variable olarak otomatik okumaz.**

- `KC_HOSTNAME`, `KC_HOSTNAME_PORT`, `KC_HOSTNAME_STRICT` → mail **linklerindeki** host için kullanılır (docker-compose’da tanımlı).
- `SMTP_*` değişkenleri → repoda **şablon** olarak `.env.local.example` içinde durur; siz aynı değerleri **Admin Console**’a girersiniz (veya ileride isteğe bağlı `kcadm` script).
- Gerçek `SMTP_PASSWORD` yalnızca `.env.local` dosyasında kalır; repoya yazılmaz.

Canlı Keycloak ayarları PostgreSQL `keycloak` veritabanında saklanır. `finance-portal-realm.json` içindeki `smtpServer` yalnızca **password’sız referans** içindir; compose otomatik import etmez.

## Ön koşullar

1. Repo kökünde: `cp .env.local.example .env.local`
2. `.env.local` içinde gerçek SMTP bilgilerini doldurun (Gmail ise [App Password](https://myaccount.google.com/apppasswords)).
3. `docker compose up -d keycloak` (veya tüm stack).

## Adım 1 — Hostname (mail linkleri)

Docker Compose zaten şunları verir:

| Değişken | Varsayılan | Amaç |
|----------|------------|------|
| `KC_HOSTNAME` | `localhost` | Reset/verify linklerindeki host |
| `KC_HOSTNAME_PORT` | `8081` | Host portu (8080 container içi) |
| `KC_HOSTNAME_STRICT` | `false` | Local dev |
| `KC_HTTP_ENABLED` | `true` | HTTP |

Keycloak’ı yeniden başlattıktan sonra Admin Console → **Realm settings → General** altında hostname ile uyumlu olduğunu kontrol edin.

## Adım 2 — Realm SMTP (Admin Console)

1. Tarayıcı: `http://localhost:8081/admin`
2. Giriş: bootstrap admin (`KC_BOOTSTRAP_ADMIN_*`, varsayılan `admin` / `admin`)
3. Sol üstten realm: **finance-portal**
4. **Realm settings** → sekme **Email**

`.env.local` içindeki değerleri girin:

| Admin Console alanı | Env değişkeni | Gmail (587) örneği |
|---------------------|---------------|---------------------|
| Host | `SMTP_HOST` | `smtp.gmail.com` |
| Port | `SMTP_PORT` | `587` |
| From | `SMTP_FROM` | Gönderen e-posta |
| From display name | `SMTP_FROM_DISPLAY_NAME` | `Finance Portal` |
| Reply to | `SMTP_REPLY_TO` | (boş olabilir) |
| Enable SSL | `SMTP_SSL` | Kapalı (`false`) |
| Enable StartTLS | `SMTP_STARTTLS` | Açık (`true`) |
| Enable Authentication | `SMTP_AUTH` | Açık |
| Username | `SMTP_USER` | Tam Gmail adresi |
| Password | `SMTP_PASSWORD` | **App Password** (normal şifre değil) |

5. **Save**
6. **Test connection** → başarılı olmalı; gerçek inbox’ı kontrol edin (spam dahil).

## Adım 3 — Login ayarları (Forgot Password)

**Realm settings → Login:**

| Ayar | Değer | Not |
|------|-------|-----|
| Forgot password | ON | Zaten `resetPasswordAllowed: true` |
| Verify email | İsteğe bağlı Faz C’de | Faz A’da SMTP testi yeterli |
| User registration | OFF önerilir | Kayıt portal → LDAP |

## Adım 4 — Forgot Password linki (tema)

Realm export: `resetPasswordAllowed: true`.

Login teması `finance-portal/login/login.ftl`:

```ftl
<@field.password ... forgotPassword=realm.resetPasswordAllowed ... />
```

Türkçe metin: `doForgotPassword=Şifremi unuttum` (`messages_tr.properties`).

Giriş: `http://localhost:8081/realms/finance-portal/protocol/openid-connect/auth?client_id=finance-portal-api&...` veya portal **Giriş Yap** → Keycloak login → şifre alanında **Şifremi unuttum**.

Görünmüyorsa: Login sekmesinde Forgot password ON mu, tema `finance-portal` mi, `login.css` linki gizliyor mu kontrol edin.

## Faz A kabul testi (geçmeden Faz B’ye geçmeyin)

- [ ] Admin Console → Email → **Test connection** başarılı
- [ ] Test maili gerçek inbox’a düştü
- [ ] Maildeki link host’u `localhost:8081` (veya sizin `KC_HOSTNAME`) — `finance-portal-keycloak` değil
- [ ] Keycloak container restart sonrası Email ayarları duruyor (DB’de kalır)
- [ ] Login ekranında **Şifremi unuttum** görünüyor

Faz B’de: forgot password ile gerçek reset maili + yeni şifre + LDAP login test edilecek.

## İsteğe bağlı: kcadm post-start script

| | Admin Console (Faz A) | kcadm init script |
|--|----------------------|-------------------|
| Artı | Basit, hata ayıklama kolay | Tekrarlanabilir deploy |
| Eksi | Manuel / dokümantasyon | Keycloak hazır olana kadar bekleme, secret yönetimi, bakım |

**Faz A’da script eklenmedi.** İleride CI/staging için değerlendirilebilir.

## OTP / mevcut akışlar

Faz A yalnızca SMTP ve hostname içerir. PKCE, LDAP federation, admin ban, OTP policy değiştirilmez.
