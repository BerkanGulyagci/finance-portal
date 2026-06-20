# Docker Configuration

Static configuration files for the services orchestrated by the root
[`docker-compose.yml`](../docker-compose.yml). Each subdirectory holds the
config a single service mounts at startup.

## Layout

```
docker/
├── apacheds/        # LDAP directory runtime data (see "Volumes" below)
├── grafana/         # Grafana provisioning (datasources, dashboards)
├── keycloak/        # Custom login theme + realm import (Dockerfile builds the image)
├── otel/            # OpenTelemetry Collector pipeline config
├── postgres/        # DB init scripts (run once on first start)
├── prometheus/      # Prometheus scrape config
└── tempo/           # Tempo (tracing backend) config
```

## Volumes: config is bind-mounted, data is a named volume

Two different mount strategies are used on purpose:

- **Static config → bind mount (read-only).** Files like `prometheus.yml`,
  `tempo.yml`, `otel-collector-config.yaml` and the Postgres init scripts live
  here in the repo so they are version-controlled and editable, and are mounted
  read-only into their container. They never change at runtime.

- **Persistent runtime data → Docker named volume.** Databases and indexes
  (`postgres`, `opensearch`, `grafana`, `prometheus`, `tempo`, `sonarqube`,
  `apacheds`) keep their constantly-rewritten state in Docker-managed named
  volumes, not in repo folders.

### ApacheDS (LDAP)

ApacheDS stores its directory data in the named volume `apacheds_data`
(not a bind mount). The `openmicroscopy/apacheds` image keeps its container
alive with a `tail -f` on its log file; on a Windows bind mount the
host↔container file bridge can stall, killing the tail and crashing the
container. A named volume avoids that bridge entirely, so the data lives at
`/var/lib/apacheds` inside the volume and the base DN / schema are bootstrapped
from the image on first start.

> The `docker/apacheds/data/` folder is the legacy bind-mount location, kept
> out of git (`.gitignore`) and no longer used by compose.

## Related

- Service definitions, ports and volume wiring: [`docker-compose.yml`](../docker-compose.yml)
- Keycloak realm import details: [`keycloak/import/README.md`](keycloak/import/README.md)
- Kubernetes manifests (cloud deploy): [`k8s/`](../k8s/)
