# Finance Portal — Backend

**English** · [Türkçe](README.md)

A REST API built on Java 21 + Spring Boot 3.2.1 in a **modular monolith + Clean Architecture** design.

> This document covers backend-specific technical details. For the project overview, setup and running, see the [main README](../../README.en.md).

## Architectural Approach

The backend is designed as a **modular monolith**: a single deployable application containing 12 modules cleanly separated by functional area (domain). Each module is split into **Clean Architecture** layers.

```
com/finance/portal/
├── market/          # Market data (stocks, crypto, FX, funds, bonds, VIOP, commodities, indices, economy)
├── portfolio/       # Portfolio, transactions, watchlist, valuation, what-if, AI analysis
├── alarm/           # Price / change / volume alerts
├── notification/    # In-app notification + email
├── news/            # Multi-source news aggregation + personalization
├── assistant/       # AI chat assistant (tool-calling)
├── newsletter/      # Newsletter subscription + digest
├── support/         # Support tickets
├── preferences/     # User preferences (cross-device sync)
├── admin/           # User management, ban (Keycloak)
├── auth/            # Authentication helpers, registration
└── common/          # Cross-cutting: security, logging, caching, errors, config
```

Each domain module consists of 4 layers:

| Layer | Content | Depends on |
|---|---|---|
| `presentation` | REST controllers (`controller`), DTOs | Application |
| `application` | Workflow services, `port` interfaces | Domain |
| `domain` | Business entities, rules | (innermost — independent) |
| `infrastructure` | Port implementations: adapters, repositories, external service clients | Application + Domain |

**Dependency rule:** Dependencies always flow from the outside in. External dependencies (database, external APIs) are abstracted via `port` interfaces; infrastructure implements them (Dependency Inversion). The domain layer is fully framework-independent.

## Main Components

- **36 REST controllers, 124 endpoints** — all under `/api/v1/**`, wrapped in `ApiResponse<T>`.
- **22 scheduled tasks** — alarm evaluation, cache warm-up, maturity settlement, newsletter digest (distributed lock via ShedLock).
- **29 external service clients** — abstracted via port/adapter (Yahoo, Binance, TCMB, TEFAS, İş Yatırım, etc.).
- **Resilience:** Last Known Good (LKG) + Resilience4j (retry / circuit breaker) + 50+ Redis cache namespaces.
- **10 JPA entities, 17 Flyway migrations** (V1–V17).

## Technologies

Spring Boot (Web, Security / OAuth2 Resource Server, Data JPA, Data Redis, Kafka, Mail, Cache, Validation, Actuator), Flyway, Lombok, Resilience4j, ShedLock, OpenTelemetry, Micrometer / Prometheus, log4j2 (JSON), springdoc-openapi (Swagger), Jsoup, Apache POI.

## Local Run (Without Docker)

> To run the whole stack with Docker, follow the setup steps in the [main README](../../README.en.md). The following is only for running the backend on its own (PostgreSQL, Redis and Keycloak must be running separately).

```bash
# Download dependencies + build
./mvnw clean package

# Run (default: PostgreSQL / Redis / Keycloak on localhost)
./mvnw spring-boot:run

# Or as a jar
java -jar target/*.jar
```

The application starts on `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.

## Testing

```bash
./mvnw test          # unit + integration tests (Testcontainers) + JaCoCo
./mvnw verify        # full verification run, as in CI
```

- **~2,700 tests** (JUnit 5, Spring Boot Test, Testcontainers, WireMock).
- Coverage report: `target/site/jacoco/index.html`.

## Database Migration

The schema is managed with **Flyway** (`src/main/resources/db/migration/`). For a new change, add a new migration:

```
V18__new_change.sql
```

Migrations run automatically on application startup. The current schema consists of 10 tables (portfolio, alarm, notification, watchlist_item, etc.).

## Configuration

Settings are managed via `application.yml` (+ profile files); they can be overridden with environment variables (`${ENV_VAR:default}`). Sensitive values live in `.env.local` (gitignored). The production profile (`prod`) halts startup if a critical secret is missing (fail-loud).

> For detailed design (Clean Architecture, port/adapter, resilience, observability), see the **Technical Design Document**.
