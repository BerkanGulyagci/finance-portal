# Faz C — Email verification (Keycloak)

## Keycloak Admin Console (canlı realm)

Realm **finance-portal** için:

1. **Realm settings → Login**
   - **Verify email**: ON
   - **User registration** (Keycloak native): OFF (kayıt portal → LDAP)

2. **Authentication → Required actions**
   - **Verify Email**: Enabled
   - **Default action**: İsteğe bağlı; LDAP kullanıcıları için backend register veya ilk login yeterli

3. **Clients → finance-portal-api**
   - Valid redirect URIs: `http://localhost:5173/auth/callback`, `http://localhost:5173/*`
   - Web origins: `http://localhost:5173`

4. SMTP (Faz A) çalışır durumda olmalı.

## Davranış

- Yeni kayıt → LDAP → backend Keycloak'ta kullanıcıyı arar (retry) → `execute-actions-email` ile VERIFY_EMAIL
- Kullanıcı Keycloak'ta yoksa kayıt yine başarılı; ilk login'de VERIFY_EMAIL ekranı
- `email_verified=false` iken `/api/portfolios/**` ve `/api/admin/**` → 403 `EMAIL_NOT_VERIFIED`
- `/api/me` filtre dışında (durum kontrolü)
- Public market/news endpointleri etkilenmez

## Manuel test

1. Yeni kullanıcı kaydı → başarı mesajı + doğrulama maili (Keycloak user varsa)
2. Login (doğrulanmamış) → `/verify-email`
3. Mail linki → doğrula → tekrar login → portföy erişimi
4. `/api/market/**` tokensız çalışır
5. Admin + forgot password regresyonu
