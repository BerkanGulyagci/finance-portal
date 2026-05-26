# SonarQube — Local Code Quality Analysis

This project uses a **self-hosted SonarQube Community** instance for code
quality, bug detection, security hotspots, and coverage tracking. It is
declared in `docker-compose.yml` under the `sonar` Compose profile so it
does **not** start with the normal `docker compose up`.

## One-time setup

```bash
# 1. Start SonarQube (takes ~60-90s to be ready; needs ~2 GB RAM)
docker compose --profile sonar up -d sonarqube

# 2. Open http://localhost:9000  (default credentials: admin / admin)
#    On first login SonarQube forces you to set a new password.

# 3. In SonarQube UI:
#       My Account -> Security -> Generate a token (e.g. name "local-cli")
#    Save the token (long opaque string). It will not be shown again.
```

## Running an analysis

From the backend directory:

```bash
cd backend/finance-portal-backend

# Produce a fresh JaCoCo report and run Sonar analysis.
./mvnw -B --no-transfer-progress verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.token=<paste-your-token-here>
```

When it finishes, open <http://localhost:9000/projects> — the
**Finance Portal Backend** project shows:

| Metric | Where it comes from |
|--------|---------------------|
| Coverage | JaCoCo XML at `target/site/jacoco/jacoco.xml` |
| Bugs | Sonar's Java rules (~600 checks) |
| Code Smells | maintainability rules |
| Security Hotspots | OWASP/CWE-aligned checks |
| Duplication | Sonar's clone detection |
| Quality Gate | configurable per project; default: `Sonar way` |

## When you are done

```bash
docker compose --profile sonar stop sonarqube   # keep data, stop process
# or
docker compose --profile sonar down              # remove containers (data in volumes survives)
```

## Notes

- SonarQube data, extensions, and logs are stored in named Docker
  volumes (`sonarqube_data`, `sonarqube_extensions`, `sonarqube_logs`)
  so analyses, settings, and tokens survive `down`.
- All Sonar properties (project key, source level, coverage report
  path, exclusions) are declared in `backend/finance-portal-backend/pom.xml`
  under `<properties>` — only the URL and token are passed on the CLI.
- This setup is **local only**. To analyse on every PR, the project
  would need either a public SonarQube URL or a switch to SonarCloud
  (private-repo SonarCloud has paid plans above ~50k LOC).
