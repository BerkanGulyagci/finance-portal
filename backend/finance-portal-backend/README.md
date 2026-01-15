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

API documentation will be available via Swagger UI once OpenAPI integration is added.

## Development Guidelines

- Follow Clean Architecture principles
- Keep domain layer framework-independent
- Use dependency injection for loose coupling
- Write unit tests for each layer
- Document public APIs and complex business logic
