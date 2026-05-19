# Keycloak Admin — USER rol ataması

## Sorun

Backend log:

```
GET /admin/realms/finance-portal/roles/USER -> 403 Forbidden
```

`finance-portal-admin-service` service account'unda **view-realm** yoksa rol adı API'den okunamaz; USER atanamaz.

## Çözüm A (önerilen — kod)

`backend/finance-portal-backend/.env.local`:

```env
KEYCLOAK_DEFAULT_USER_ROLE_ID=<USER rolünün UUID'si>
```

USER rol ID'sini bulmak:

1. Keycloak Admin → realm **finance-portal**
2. **Realm roles** → **USER**
3. URL veya role details'teki `id` değerini kopyala

Backend yeniden başlat → yeni register veya `/profile` (GET /api/me) USER atar.

## Çözüm B (Keycloak yetkileri)

**Clients** → `finance-portal-admin-service` → **Service account roles** → **realm-management**:

- `manage-users` (rol mapping için)
- `view-realm` (GET /roles/USER için, opsiyonel A varsa)

Kaydet → backend restart.

## memoa gibi mevcut kullanıcılar

1. `.env.local` içine `KEYCLOAK_DEFAULT_USER_ROLE_ID` ekle
2. Backend restart
3. memoa ile login → `/profile` aç (GET /api/me USER atar)
4. Keycloak'ta Role mapping → USER görünmeli
5. Logout + login → JWT'de USER
