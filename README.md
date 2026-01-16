# Docker Service Manager

[![CI](https://github.com/AmmannChristian/docker-service-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/AmmannChristian/docker-service-manager/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/AmmannChristian/docker-service-manager/graph/badge.svg)](https://app.codecov.io/gh/AmmannChristian/docker-service-manager)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://github.com/AmmannChristian/docker-service-manager/blob/main/LICENSE)

A REST API for managing Docker containers, built with Quarkus. This service provides endpoints for container lifecycle management including starting, stopping, restarting, and updating containers, as well as log streaming capabilities via Server-Sent Events.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Authentication](#authentication)
- [Development](#development)
- [Testing](#testing)
- [Docker](#docker)
- [Contributing](#contributing)
- [License](#license)

## Features

- Container lifecycle management (start, stop, restart, update)
- Container listing with filtering options
- Log retrieval and real-time log streaming via SSE
- OpenID Connect authentication with ZITADEL support
- Role-based access control
- OpenAPI documentation
- Health checks for container orchestration
- Multi-architecture Docker images (amd64, arm64)
- Native executable support via GraalVM

## Requirements

- Java 21 or later
- Maven 3.9 or later
- Docker Engine (for container management)
- OIDC Provider (ZITADEL recommended, but any compliant provider works)

## Quick Start

1. Clone the repository:

```bash
git clone https://github.com/AmmannChristian/docker-service-manager.git
cd docker-service-manager
```

2. Configure the OIDC settings (see Configuration section below).

3. Run in development mode:

```bash
./mvnw quarkus:dev
```

The API will be available at `http://localhost:8080`. The Quarkus Dev UI is accessible at `http://localhost:8080/q/dev/`.

## Configuration

### Environment Variables

The application requires the following environment variables for OIDC authentication:

| Variable | Description | Required |
|----------|-------------|----------|
| `OIDC_AUTH_SERVER_URL` | OIDC provider base URL | Yes |
| `OIDC_CLIENT_ID` | OAuth2 client ID | Yes |
| `OIDC_JWT_KEY_FILE` | Path to private key file for JWT authentication | Yes |
| `OIDC_JWT_KEY_ID` | Key ID for JWT signing | Yes |
| `OIDC_JWT_LIFESPAN` | JWT token lifespan in seconds (default: 300) | No |
| `OIDC_JWT_AUDIENCE` | JWT audience claim | No |

### Example Configuration

Create a `.env` file or set environment variables:

```bash
export OIDC_AUTH_SERVER_URL=https://your-zitadel-instance.com
export OIDC_CLIENT_ID=your-client-id
export OIDC_JWT_KEY_FILE=/path/to/private-key.json
export OIDC_JWT_KEY_ID=your-key-id
```

### Docker Socket Access

The application requires access to the Docker socket. When running in Docker, mount the socket:

```bash
docker run -v /var/run/docker.sock:/var/run/docker.sock your-image
```

## API Reference

All endpoints require authentication and the `ADMIN_ROLE` role.

### Base URL

```
/api/v1/containers
```

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | List all containers |
| POST | `/{id}/start` | Start a container |
| POST | `/{id}/stop` | Stop a container |
| POST | `/{id}/restart` | Restart a container |
| POST | `/{id}/update` | Pull latest image and recreate container |
| GET | `/{id}/logs` | Get container logs |
| GET | `/{id}/logs/stream` | Stream container logs via SSE |

### List Containers

```bash
curl -X GET "http://localhost:8080/api/v1/containers?all=true" \
  -H "Authorization: Bearer <token>"
```

Query parameters:
- `all` (boolean, default: false): Include stopped containers

### Start Container

```bash
curl -X POST "http://localhost:8080/api/v1/containers/{containerId}/start" \
  -H "Authorization: Bearer <token>"
```

### Stop Container

```bash
curl -X POST "http://localhost:8080/api/v1/containers/{containerId}/stop" \
  -H "Authorization: Bearer <token>"
```

### Restart Container

```bash
curl -X POST "http://localhost:8080/api/v1/containers/{containerId}/restart" \
  -H "Authorization: Bearer <token>"
```

### Update Container

Pulls the latest version of the container image and recreates the container with the same configuration.

```bash
curl -X POST "http://localhost:8080/api/v1/containers/{containerId}/update" \
  -H "Authorization: Bearer <token>"
```

### Get Container Logs

```bash
curl -X GET "http://localhost:8080/api/v1/containers/{containerId}/logs?tail=100" \
  -H "Authorization: Bearer <token>"
```

Query parameters:
- `tail` (integer, default: 100, range: 1-10000): Number of log lines to return

### Stream Container Logs

```bash
curl -X GET "http://localhost:8080/api/v1/containers/{containerId}/logs/stream?follow=true" \
  -H "Authorization: Bearer <token>" \
  -H "Accept: text/event-stream"
```

Query parameters:
- `follow` (boolean, default: true): Continue streaming new log entries

### OpenAPI Documentation

When running the application, the OpenAPI specification is available at:
- Swagger UI: `http://localhost:8080/q/swagger-ui`
- OpenAPI JSON: `http://localhost:8080/q/openapi`

## Authentication

The application uses OpenID Connect for authentication. It supports both JWT tokens (verified locally) and opaque tokens (verified via introspection).

### ZITADEL Integration

This application includes a custom security identity augmentor that extracts roles from ZITADEL-specific JWT claims:

- `urn:zitadel:iam:org:project:roles` - Standard ZITADEL roles claim
- `urn:zitadel:iam:org:project:{projectId}:roles` - Project-specific roles

The augmentor also falls back to standard claims:
- `groups` - Standard groups claim
- `scope` - OAuth2 scopes as roles

### Required Role

All container management endpoints require the `ADMIN_ROLE` role.

## Development

### Prerequisites

- JDK 21
- Maven 3.9+
- Docker (for running containers)

### Running in Dev Mode

Development mode enables live coding with automatic restart on code changes:

```bash
./mvnw quarkus:dev
```

### Code Style

The project uses Google Java Format via Spotless. To check formatting:

```bash
./mvnw spotless:check
```

To automatically format code:

```bash
./mvnw spotless:apply
```

### Building

Build the application:

```bash
./mvnw package
```

Build without running tests:

```bash
./mvnw package -DskipTests
```

### Running the JAR

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Testing

### Running Tests

```bash
./mvnw test
```

### Running Tests with Coverage

```bash
./mvnw verify
```

Coverage reports are generated in `target/site/jacoco/`.

### Coverage Requirements

The project enforces a minimum line coverage of 90%. The build will fail if coverage falls below this threshold.

## Docker

### Building Images

The project includes a multi-stage Dockerfile with several targets:

**Development image** (with hot reload):
```bash
docker build --target dev -t docker-service-manager:dev .
```

**Production image** (JVM mode):
```bash
docker build --target prod -t docker-service-manager:latest .
```

**Native image** (GraalVM):
```bash
docker build --target prod-native -t docker-service-manager:native .
```

### Running with Docker

```bash
docker run -d \
  --name docker-service-manager \
  -p 9000:9000 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e OIDC_AUTH_SERVER_URL=https://your-zitadel.com \
  -e OIDC_CLIENT_ID=your-client-id \
  -e OIDC_JWT_KEY_FILE=/keys/private-key.json \
  -e OIDC_JWT_KEY_ID=your-key-id \
  -v /path/to/keys:/keys:ro \
  docker-service-manager:latest
```

### Docker Compose

```yaml
version: '3.8'

services:
  docker-service-manager:
    image: ghcr.io/ammannchristian/docker-service-manager:latest
    ports:
      - "9000:9000"
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
      - ./keys:/keys:ro
    environment:
      OIDC_AUTH_SERVER_URL: https://your-zitadel.com
      OIDC_CLIENT_ID: your-client-id
      OIDC_JWT_KEY_FILE: /keys/private-key.json
      OIDC_JWT_KEY_ID: your-key-id
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/q/health/live"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s
```

### Pre-built Images

Multi-architecture images are available on GitHub Container Registry:

```bash
docker pull ghcr.io/ammannchristian/docker-service-manager:latest
```

Available tags:
- `latest` - Latest build from main branch
- `x.y.z` - Specific version (e.g., `1.0.0`)
- `x.y` - Minor version (e.g., `1.0`)
- `sha-xxxxxxx` - Specific commit

## Project Structure

```
docker-service-manager/
├── src/
│   ├── main/
│   │   ├── java/com/ammann/servicemanager/
│   │   │   ├── config/          # CDI producers and configuration
│   │   │   ├── dto/             # Data transfer objects
│   │   │   ├── health/          # Health check implementations
│   │   │   ├── properties/      # Configuration properties
│   │   │   ├── resource/        # REST endpoints
│   │   │   ├── security/        # Security augmentors
│   │   │   └── service/         # Business logic
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/                # Unit and integration tests
├── .github/
│   └── workflows/               # CI/CD pipelines
├── Dockerfile                   # Multi-stage Docker build
├── pom.xml                      # Maven configuration
└── README.md
```

## Contributing

Contributions are welcome. Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Make your changes
4. Ensure tests pass (`./mvnw verify`)
5. Ensure code is formatted (`./mvnw spotless:apply`)
6. Commit your changes
7. Push to your branch
8. Open a Pull Request

Please ensure your PR:
- Includes tests for new functionality
- Maintains or improves code coverage
- Follows the existing code style
- Updates documentation if needed

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.