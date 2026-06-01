-- Keycloak için ayrı veritabanı.
--
-- Bu script SADECE BOŞ bir Postgres data dizininde (ilk başlatma) çalışır;
-- mevcut kurulumda (volume zaten doluysa) Postgres tarafından ATLANIR.
-- POSTGRES_DB (finance_portal) zaten yaratılır; burada Keycloak'ın kullandığı
-- `keycloak` veritabanını ekliyoruz (compose: KC_DB_URL=.../keycloak, KC_DB_USERNAME=berkan).
--
-- \gexec ile idempotent: keycloak DB zaten varsa CREATE çalıştırılmaz.
SELECT 'CREATE DATABASE keycloak'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec
