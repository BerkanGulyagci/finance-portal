# Portal profil yönetimi (Keycloak Admin API)

## Endpointler

| Method | Path | Açıklama |
|--------|------|----------|
| GET | `/api/me` | Profil okuma |
| PATCH | `/api/me/profile` | Ad / soyad |
| PATCH | `/api/me/email` | Email + VERIFY_EMAIL |
| PATCH | `/api/me/password` | Mevcut şifre doğrulama + yeni şifre |

Tümü authenticated. `/api/me/**` email verification filtresinden muaf.

## Email değişimi sonrası

`requiresReLogin: true` — JWT içindeki `email_verified` eski kalır. Frontend oturumu temizler ve `/verify-email` sayfasına yönlendirir.

## Şifre değişimi

1. Resource owner password grant (`finance-portal-api`, direct access grants realm referansında açık)
2. Admin API `reset-password`
3. Tüm oturumlar logout

**LDAP:** Şifre LDAP’ta tutuluyorsa Admin reset Keycloak’ta kalabilir; manuel test gerekir.

**OTP:** Direct grant flow’da koşullu OTP varsa doğrulama başarısız olabilir; modal içinde Keycloak Account Console fallback linki.
