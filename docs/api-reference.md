# Docker Service Manager -- API Reference

## 1. Overview

The Docker Service Manager exposes a RESTful HTTP API for Docker container lifecycle management. All endpoints are defined under the base path `/api/v1/containers` and require bearer token authentication with the `SUPER_ADMIN_ROLE` role. The API produces JSON responses for container data and supports Server-Sent Events for real-time log streaming.

The OpenAPI specification is available at runtime through the SmallRye OpenAPI integration:

| Endpoint | Description |
|----------|-------------|
| `/q/openapi` | OpenAPI 3.0 specification (JSON) |
| `/q/swagger-ui` | Interactive Swagger UI |

## 2. Authentication

All endpoints under `/api/v1/*` require a valid bearer token in the `Authorization` header. The service accepts both JWT tokens (verified locally via JWKS) and opaque tokens (verified via introspection against the configured OIDC provider).

**Required Role:** `SUPER_ADMIN_ROLE`

Requests without a valid token receive a `401 Unauthorized` response. Requests with a valid token but without the required role receive a `403 Forbidden` response.

## 3. Endpoint Summary

| Method | Path | Description | Response |
|--------|------|-------------|----------|
| `GET` | `/api/v1/containers` | List all containers | `200` with JSON array |
| `POST` | `/api/v1/containers/{id}/start` | Start a stopped container | `204` No Content |
| `POST` | `/api/v1/containers/{id}/stop` | Stop a running container | `204` No Content |
| `POST` | `/api/v1/containers/{id}/restart` | Restart a container | `204` No Content |
| `POST` | `/api/v1/containers/{id}/update` | Pull latest image and recreate container | `204` No Content |
| `GET` | `/api/v1/containers/{id}/logs` | Retrieve historical container logs | `200` with plain text |
| `GET` | `/api/v1/containers/{id}/logs/stream` | Stream container logs via SSE | `200` with event stream |

## 4. Path Parameters

### Container ID

All endpoints that operate on a specific container require the container identifier as a path parameter.

| Parameter | Type | Pattern | Description |
|-----------|------|---------|-------------|
| `id` | `string` | `^[a-fA-F0-9]{12,64}$` | Docker container ID (12 to 64 hexadecimal characters) |

Requests with an identifier that does not match the required pattern receive a `400 Bad Request` response with a validation error message.

## 5. Endpoint Details

### 5.1 List Containers

Retrieves a list of Docker containers from the host system.

**Request**

```
GET /api/v1/containers?all={showAll}
```

| Query Parameter | Type | Default | Description |
|-----------------|------|---------|-------------|
| `all` | `boolean` | `false` | When `true`, include stopped containers in the result |

**Response -- 200 OK**

Returns a JSON array of `ContainerInfoDTO` objects.

```json
[
  {
    "id": "a1b2c3d4e5f6a1b2c3d4e5f6",
    "name": "/nginx-proxy",
    "image": "nginx:latest",
    "state": "running",
    "status": "Up 5 hours"
  }
]
```

**Response Schema: ContainerInfoDTO**

| Field | Type | Description |
|-------|------|-------------|
| `id` | `string` | Docker container identifier |
| `name` | `string` | Container name (prefixed with `/`) |
| `image` | `string` | Docker image reference |
| `state` | `string` | Container state (e.g., `running`, `exited`) |
| `status` | `string` | Human-readable status description |

### 5.2 Start Container

Starts a stopped container.

**Request**

```
POST /api/v1/containers/{id}/start
```

**Response -- 204 No Content**

The container was started successfully. The response body is empty.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role or container is blacklisted |
| `404` | Container not found |
| `500` | Docker daemon error |

Note: The start operation is not subject to blacklist enforcement.

### 5.3 Stop Container

Stops a running container with a 10-second timeout.

**Request**

```
POST /api/v1/containers/{id}/stop
```

**Response -- 204 No Content**

The container was stopped successfully. The response body is empty.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role or container is blacklisted |
| `404` | Container not found |
| `500` | Docker daemon error |

**Blacklist Enforcement:** This operation checks the target container against the configured service blacklist before execution. If the container is protected, the service returns HTTP 403 with the following JSON body:

```json
{
  "error": "Forbidden",
  "message": "Container is blacklisted and cannot be modified: <containerId>",
  "containerId": "<containerId>"
}
```

### 5.4 Restart Container

Restarts a running or stopped container with a 10-second timeout.

**Request**

```
POST /api/v1/containers/{id}/restart
```

**Response -- 204 No Content**

The container was restarted successfully. The response body is empty.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role or container is blacklisted |
| `404` | Container not found |
| `500` | Docker daemon error |

**Blacklist Enforcement:** This operation is subject to blacklist validation. See Section 5.3 for the error response format.

### 5.5 Update Container

Pulls the latest version of the container's image and recreates the container with the updated image while preserving all configuration. This operation performs the following steps in sequence: pull image, stop container, capture network connections, remove container, create new container with preserved configuration, reconnect networks, and start the new container.

**Request**

```
POST /api/v1/containers/{id}/update
```

**Response -- 204 No Content**

The container was updated and restarted successfully. The response body is empty.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role or container is blacklisted |
| `404` | Container not found |
| `500` | Docker daemon error or image pull failure |

**Blacklist Enforcement:** This operation is subject to blacklist validation. See Section 5.3 for the error response format.

**Preserved Configuration Properties:**

The following container configuration properties are transferred from the original container to the replacement:

| Category | Properties |
|----------|------------|
| Container Config | Environment variables, labels, exposed ports, command, entrypoint, working directory, user, volumes, health check |
| Host Config | Port bindings, volume mounts, network mode, restart policy, resource limits |
| Network Settings | All connected networks with original aliases |

### 5.6 Get Container Logs

Retrieves the most recent log lines from a container.

**Request**

```
GET /api/v1/containers/{id}/logs?tail={lines}
```

| Query Parameter | Type | Default | Range | Description |
|-----------------|------|---------|-------|-------------|
| `tail` | `integer` | `100` | 1 -- 10000 | Number of log lines to retrieve from the end |

**Response -- 200 OK**

Returns the container logs as plain text (`text/plain`). Both standard output and standard error streams are included.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format or tail parameter out of range |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role |
| `404` | Container not found |
| `500` | Docker daemon error |

### 5.7 Stream Container Logs

Streams container logs in real time using Server-Sent Events (SSE). Each log line is emitted as a separate SSE event with `text/plain` content type.

**Request**

```
GET /api/v1/containers/{id}/logs/stream?follow={follow}
Accept: text/event-stream
```

| Query Parameter | Type | Default | Description |
|-----------------|------|---------|-------------|
| `follow` | `boolean` | `true` | When `true`, continue streaming new log entries as they are produced |

**Response -- 200 OK**

Returns a stream of Server-Sent Events. Each event contains a single log line with a timestamp prefix. The stream includes both standard output and standard error.

```
data: 2026-01-15T10:30:00.000Z Container started successfully

data: 2026-01-15T10:30:01.123Z Listening on port 8080
```

The stream continues until the client disconnects or the container stops (when `follow=true`). When `follow=false`, the stream completes after delivering existing log content.

**Error Responses**

| Status | Condition |
|--------|-----------|
| `400` | Invalid container ID format |
| `401` | Missing or invalid authentication token |
| `403` | Insufficient role |
| `404` | Container not found |
| `500` | Docker daemon error |

## 6. Health and Management Endpoints

Health check and management endpoints are served on a dedicated management port (default: 9090) and do not require authentication.

| Endpoint | Description |
|----------|-------------|
| `/q/health/live` | Liveness probe -- returns UP when the process is running |
| `/q/health/ready` | Readiness probe |
| `/q/health` | Combined health status |

## 7. Error Response Format

### Validation Errors

Requests that fail input validation (Bean Validation constraints) return a `400 Bad Request` response with details about the constraint violation.

### Blacklist Errors

Requests targeting a protected container return a structured JSON error:

| Field | Type | Description |
|-------|------|-------------|
| `error` | `string` | Error category (`"Forbidden"`) |
| `message` | `string` | Human-readable error description |
| `containerId` | `string` | The identifier of the blacklisted container |

### Authentication Errors

| Status | Condition |
|--------|-----------|
| `401 Unauthorized` | No bearer token provided, or the token is invalid or expired |
| `403 Forbidden` | Valid token but the principal lacks the `SUPER_ADMIN_ROLE` role |

## 8. CORS Configuration

Cross-Origin Resource Sharing is enabled by default with the following settings:

| Setting | Default Value |
|---------|---------------|
| Origins | `/.*/` (all origins) |
| Methods | `GET, POST, PUT, DELETE, OPTIONS` |
| Allowed Headers | `accept, authorization, content-type, x-requested-with` |
| Exposed Headers | `content-disposition` |

CORS preflight (OPTIONS) requests to `/api/*` paths are permitted without authentication.