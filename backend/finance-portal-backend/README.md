# Finance Portal Backend

Spring Boot backend application implementing Clean Architecture principles for the Finance Portal system.

## Overview

This backend service provides RESTful APIs for the Finance Portal application. It is built with a focus on maintainability, scalability, and adherence to enterprise software development standards.

## Architecture

The application follows **Clean Architecture** (also known as Hexagonal Architecture or Ports and Adapters), organizing code into distinct layers:

### Layer Structure

```
com.finance.portal/
├── presentation/       # Controllers, DTOs, API layer
│   └── controller/     # REST controllers
├── application/        # Use cases, business logic orchestration
│   └── service/        # Application services
├── domain/            # Business entities, domain logic
└── infrastructure/    # External integrations, configurations
    └── config/        # Spring configurations
```

### Layer Responsibilities

- **Presentation Layer**: Handles HTTP requests/responses, input validation, and API contracts
- **Application Layer**: Orchestrates business workflows, coordinates between domain and infrastructure
- **Domain Layer**: Contains core business logic, entities, and domain rules (framework-independent)
- **Infrastructure Layer**: Manages external dependencies (databases, message queues, external APIs)

## Current Features

### Health Check Endpoint

- **Endpoint**: `GET /api/health`
- **Response**: `Finance Portal Backend is running`
- **Purpose**: Verify application status and readiness

### Crypto Market API (Powered by CoinGecko)

- **Endpoint**: `GET /api/market/crypto?page=0&size=20`
- **Description**: Returns paginated crypto market list in TRY. Data is provided by [CoinGecko](https://www.coingecko.com/) (Demo API).
- **Query params**: `page` (0-based, default 0), `size` (1–100, default 20)

## Technology Stack

- **Framework**: Spring Boot 3.2.1
- **Java Version**: 17
- **Build Tool**: Maven
- **Web**: Spring Web (REST APIs)

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+ (or use included Maven wrapper)

### Build the Application

```bash
./mvnw clean install
```

### Run the Application

```bash
./mvnw spring-boot:run
```

The application starts on port `8080` by default.

### Configuration

Configuration is managed through `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

spring:
  application:
    name: finance-portal-backend
```

## Testing

```bash
./mvnw test
```

## Extensibility

The current architecture is designed to easily accommodate future enhancements:

- **Database Integration**: Add repositories in infrastructure layer
- **Security**: Implement authentication/authorization in presentation layer
- **Message Queue**: Add Kafka producers/consumers in infrastructure layer
- **Caching**: Integrate Redis in infrastructure layer
- **External APIs**: Add clients in infrastructure layer

Each addition can be made without affecting the core business logic in the domain layer.

## API Documentation

API documentation will be available via Swagger UI once OpenAPI integration is added. For Crypto Market API, see "Testing the Crypto API" below. **Crypto market data: Powered by CoinGecko.**

### Testing the Crypto API (local)

Our API (defaults: page=0, size=20):

```bash
curl -s "http://localhost:8080/api/market/crypto?page=0&size=20"
```

**Cache check:** Call the same URL twice. The first request logs `Calling CoinGecko /coins/markets ...` (cache miss); the second request within TTL (45s) does not call CoinGecko (cache hit, no such log).

```bash
curl -s "http://localhost:8080/api/market/crypto?page=0&size=20" | jq .
curl -s "http://localhost:8080/api/market/crypto?page=0&size=20" | jq .
```

Direct CoinGecko Demo API check (same parameters: `vs_currency=try`, `per_page=20`, `page=1`):

```bash
curl -s -H "x-cg-demo-api-key: CG-B1V3coqfjBN6Q5Ff8j6AnpZf" \
  "https://api.coingecko.com/api/v3/coins/markets?vs_currency=try&order=market_cap_desc&per_page=20&page=1&sparkline=false&price_change_percentage=24h"
```

## Development Guidelines

- Follow Clean Architecture principles
- Keep domain layer framework-independent
- Use dependency injection for loose coupling
- Write unit tests for each layer
- Document public APIs and complex business logic
