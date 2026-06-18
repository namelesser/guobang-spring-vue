# AGENTS.md — Repository Guidelines

## Project Overview

**Guobang Transport** — a transportation management system built with Spring Boot 3 + Vue 3 + PostgreSQL.

### Tech Stack

- **Backend**: Spring Boot 3.3.6, Java 17+, MyBatis-Plus 3.5.9, PostgreSQL
- **Frontend**: Vue 3.5, Naive UI 2.44, Vite 8, TypeScript, UnoCSS
- **Based on**: Soybean Admin template (package.json name: "soybean-admin")

### Ports

- Backend: **8000** (configurable via `SERVER_PORT`)
- Frontend: **9527** (vite.config.ts)

## Quick Start Commands

### Backend

```bash
# Start backend (requires environment variables)
TRANSPORT_AUTH_PASSWORD=552300 TRANSPORT_AUTH_SECRET=guobang-secret-key-2024 PG_PASSWORD=552300 ./mvnw spring-boot:run
```

**Required environment variable**: `TRANSPORT_AUTH_SECRET` — if not set, all APIs return 503.

### Frontend

```bash
cd frontend
pnpm install
pnpm dev          # Starts dev server on port 9527 (vite --mode test)
```

### Build

```bash
# Frontend
pnpm build        # Production build (vite build --mode prod)
pnpm build:test   # Test build

# Backend
./mvnw package    # Creates JAR
```

## Code Quality & Pre-commit

Pre-commit hook runs: `pnpm typecheck && pnpm lint && pnpm fmt && git diff --exit-code`

```bash
# Run individually:
pnpm typecheck    # vue-tsc --noEmit --skipLibCheck
pnpm lint         # oxlint --fix && eslint --fix .
pnpm fmt          # oxfmt
```

## Architecture

### Backend Structure

```
backend/src/main/java/com/guobang/transport/
├── common/          # Shared utilities (Api, BusinessException, DbSupport)
├── auth/            # Cookie-based auth filter
├── record/          # Transport records CRUD + export
├── review/          # Weighbridge review
├── collection/      # Dropdown options (sites, vehicles, materials)
├── rate/            # Freight rates
├── report/          # Monthly reports
├── image/           # Image upload (AWS S3 compatible)
└── ocr/             # OCR processing
```

### Frontend Structure

```
frontend/src/
├── views/
│   ├── records/     # Transport records table
│   ├── review/      # Weighbridge review
│   ├── rates/       # Freight rates management
│   ├── collections/ # Dropdown options admin
│   ├── report/      # Monthly reports
│   ├── data-quality/# Data quality checks
│   ├── images/      # Image management
│   └── ocr/         # OCR interface
├── service/
│   ├── api/         # API client functions (business.ts, auth.ts)
│   └── request/     # Axios setup, token handling
└── store/           # Pinia stores
```

## API Conventions

### Response Format

All API responses use `{ok: true/false}` — **not** standard HTTP status codes for success checks.

```typescript
// Frontend success check (service/request/index.ts:30-32)
isBackendSuccess(response) {
  return response.data?.ok === true;
}
```

### Backend Response Pattern

```java
// Success
return Api.ok();                           // {ok: true}
return Api.ok("key", value);               // {ok: true, key: value}
return Api.ok(Map.of("k1", v1, "k2", v2)); // {ok: true, k1: v1, k2: v2}

// Error
return Api.error("message", HttpStatus.BAD_REQUEST); // {ok: false, error: "message", status: 400}
```

**Note**: `Api.ok()` does NOT accept a single List parameter. Use `Api.ok("key", list)`.

### Auth

- Cookie-based session authentication
- Frontend stores token in localStorage for route guards only
- 401 responses trigger automatic logout

### Common Parameters

- `draw` — DataTables draw counter (required for pagination)
- `start` / `length` — Pagination offset/limit
- `category` — Filter collections by type (site, vehicle, material, etc.)

## Database

- **PostgreSQL** with MyBatis-Plus
- Underscore-to-camelCase mapping enabled
- ID type: auto-increment

### Connection

```yaml
# application.yml
spring.datasource:
  url: jdbc:postgresql://${PG_HOST:127.0.0.1}:${PG_PORT:5432}/${PG_DATABASE:transport}?sslmode=disable
  username: ${PG_USER:transport}
  password: ${PG_PASSWORD:}
```

## Key Libraries & Versions

### Frontend

- `vue`: 3.5.34
- `naive-ui`: 2.44.1
- `vite`: 8.0.12
- `pinia`: 3.0.4
- `vue-router`: 5.0.7
- `typescript`: 6.0.3
- `oxlint`: ^1.64.0
- `oxfmt`: ^0.49.0

### Backend

- Spring Boot: 3.3.6
- MyBatis-Plus: 3.5.9
- AWS S3 SDK: 2.29.52 (for image storage)

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `TRANSPORT_AUTH_SECRET` | **Required** — JWT secret for auth | (none, returns 503 if unset) |
| `TRANSPORT_AUTH_PASSWORD` | Login password | (none) |
| `PG_PASSWORD` | PostgreSQL password | (empty) |
| `PG_HOST` | PostgreSQL host | 127.0.0.1 |
| `PG_PORT` | PostgreSQL port | 5432 |
| `PG_DATABASE` | Database name | transport |
| `PG_USER` | Database user | transport |
| `SERVER_PORT` | Backend port | 8000 |
| `BACKEND_BASE_URL` | Backend URL for image uploads | http://localhost:8000 |
| `PADDLE_API_URL` | PaddleOCR API endpoint | https://paddleocr.aistudio-app.com/api/v2/ocr/jobs |
| `PADDLE_API_TOKEN` | PaddleOCR API token | (hardcoded default) |
| `PADDLE_MODEL` | PaddleOCR model name | PaddleOCR-VL-1.6 |
| `PADDLE_TIMEOUT_SEC` | PaddleOCR job timeout (seconds) | 900 |
| `PADDLE_POLL_INTERVAL_SEC` | PaddleOCR poll interval (seconds) | 4 |
| `BAIDU_API_KEY` | Baidu OCR API key | (hardcoded default) |
| `BAIDU_SECRET_KEY` | Baidu OCR secret key | (hardcoded default) |

## Gotchas

1. **API success check**: Use `response.data?.ok === true`, not HTTP status codes
2. **Auth filter**: Returns 503 if `TRANSPORT_AUTH_SECRET` is not set
3. **Export endpoints**: Return `text/csv` with `Content-Disposition` header for Excel download
4. **Image uploads**: Stored via AWS S3 compatible storage, configured via `BACKEND_BASE_URL`
5. **Frontend proxy**: `/api` requests proxy to `http://localhost:8000` in dev mode

## Vite Aliases

- `@` → `./src`
- `~` → `./` (project root)
