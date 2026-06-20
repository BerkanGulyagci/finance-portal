# Keycloak realm auto-import

On `docker compose up` this folder is mounted into Keycloak as
`/opt/keycloak/data/import` and loaded automatically via `--import-realm` into
an **empty Keycloak** (if a realm already exists it is SKIPPED, not overwritten).

## What is committed vs. kept local

A **sanitized** `finance-portal-realm.json` (placeholder/empty secrets, no real
users) is committed so the stack boots on a fresh clone. The **real** export —
which contains live passwords/secrets and real users — must never be committed:
keep it as `*.local.json`, which is gitignored.

## New machine / server setup

Move the real realm export to a new machine **by hand** (NOT via GitHub):

```bash
# On the current machine, take an export (read-only, doesn't touch the running Keycloak):
docker exec finance-portal-keycloak /opt/keycloak/bin/kc.sh export \
  --file /tmp/finance-portal-realm.json --realm finance-portal --users same_file
docker cp finance-portal-keycloak:/tmp/finance-portal-realm.json \
  docker/keycloak/import/finance-portal-realm.local.json

# Copy to the new machine (NOT via GitHub):
scp docker/keycloak/import/finance-portal-realm.local.json \
  user@server:~/finance-portal/docker/keycloak/import/
```

LDAP user passwords live in ApacheDS; also move `backup/apacheds-export.ldif`
and import it into ApacheDS (otherwise federated users cannot log in).
