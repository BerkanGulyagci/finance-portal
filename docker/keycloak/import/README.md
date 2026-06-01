# Keycloak realm auto-import

Bu klasör `docker compose up`'ta Keycloak'a `/opt/keycloak/data/import` olarak mount edilir
ve `--import-realm` ile **boş bir Keycloak'a** otomatik yüklenir (mevcut realm varsa ATLANIR,
ezilmez).

## Yeni makine/sunucu kurulumu

Realm export'u (kullanıcılar + roller + ADMIN + LDAP federation dahil) **şifre/secret içerir**,
bu yüzden `.gitignore`'ludur (`*.json` git'e gitmez — GitHub'a yüklenmez). Yeni makineye **elle**
taşınır:

```bash
# Mevcut makinede export al (çalışan Keycloak'a dokunmaz, salt-okunur):
docker exec finance-portal-keycloak /opt/keycloak/bin/kc.sh export \
  --file /tmp/finance-portal-realm.json --realm finance-portal --users same_file
docker cp finance-portal-keycloak:/tmp/finance-portal-realm.json \
  docker/keycloak/import/finance-portal-realm.json

# Yeni makineye kopyala (GitHub üzerinden DEĞİL):
scp docker/keycloak/import/finance-portal-realm.json \
  kullanici@sunucu:~/finance-portal/docker/keycloak/import/
```

LDAP kullanıcılarının şifreleri ApacheDS'tedir; `backup/apacheds-export.ldif`'i de taşıyıp
ApacheDS'e import et (yoksa federated kullanıcılar giriş yapamaz).
