# Finance Portal

A modern, enterprise-grade financial portal application built with clean architecture principles and industry best practices.

## Overview

Finance Portal is a comprehensive financial management system designed to demonstrate professional software engineering practices. This project serves as a technical assessment showcase, implementing scalable architecture patterns and modern development workflows.

## Technology Stack

- **Backend Framework**: Spring Boot 3.2.1
- **Language**: Java 17
- **Architecture**: Clean Architecture (Hexagonal Architecture)
- **API Design**: RESTful APIs
- **Build Tool**: Maven

## Project Structure

```
finance-portal/
├── backend/                    # Backend services
│   └── finance-portal-backend/ # Spring Boot application
├── docker/                     # Docker configurations
├── docs/                       # Project documentation
│   ├── system-analysis.md      # System requirements and analysis
│   ├── architecture.md         # Architecture decisions and diagrams
│   └── decisions.md            # Technical decision records
└── README.md                   # This file
```

## Current Status

✅ **Completed:**
- Repository structure initialized
- Spring Boot backend skeleton with Clean Architecture
- Health check endpoint (`GET /api/health`)
- Professional documentation layout

🚧 **Planned Features:**
- Database integration (PostgreSQL/MySQL)
- Security & Authentication (Spring Security, JWT)
- Message Queue integration (Apache Kafka)
- Caching layer (Redis)
- Docker containerization
- API documentation (OpenAPI/Swagger)
- Comprehensive test coverage
- CI/CD pipeline

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6+

### Running the Backend

```bash
cd backend/finance-portal-backend
./mvnw spring-boot:run
```

The application will start on `http://localhost:8080`

### Testing the Health Endpoint

```bash
curl http://localhost:8080/api/health
```

Expected response: `Finance Portal Backend is running`

## Architecture

This project follows **Clean Architecture** principles, ensuring:
- **Separation of Concerns**: Clear boundaries between layers
- **Independence**: Business logic independent of frameworks
- **Testability**: Easy to test each layer in isolation
- **Maintainability**: Easy to understand and modify

For detailed architecture documentation, see [docs/architecture.md](docs/architecture.md)

## Contributing

This is a technical assessment project. Contributions are not currently accepted.

## License

This project is created for educational and assessment purposes.
