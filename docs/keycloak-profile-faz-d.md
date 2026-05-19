# Faz D — Profil sayfası ve Keycloak Account Console

## Backend

- `GET /api/me` — JWT `sub` ile Keycloak Admin API’den güncel profil
- `EmailVerifiedFilter` — `/api/me` muaf (doğrulanmamış kullanıcı da profil görebilir)
- Keycloak erişilemezse JWT claim fallback

## Frontend env

`frontend/finance-portal-frontend/.env` (`.env.example` kopyası):

```
VITE_KEYCLOAK_URL=http://localhost:8081
VITE_KEYCLOAK_REALM=finance-portal
```

## Keycloak 26 Account Console URL’leri

Path-based router (hash değil):

| Amaç | URL |
|------|-----|
| Ana | `http://localhost:8081/realms/finance-portal/account/` |
| Profil / email | `.../account/personal-info` |
| Şifre | `.../account/security/signing-in` |

Account Console ayrı oturum ister; PKCE token’ı otomatik taşınmaz — kullanıcı gerekirse Keycloak’ta tekrar giriş yapar.

## Manuel test

1. Giriş yap → Header’da profil menüsü → `/profile` bilgileri
2. “Bilgilerimi Düzenle” → Account Console → ad/email değiştir → `/profile` yenile
3. “Şifremi Değiştir” → signing-in sayfası açılır
4. Email doğrulanmamış kullanıcı `/profile` erişebilir; portföy engelli kalır
5. Admin panel / ban / forgot password regresyonu
