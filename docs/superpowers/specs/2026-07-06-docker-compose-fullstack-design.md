# Docker Compose Local Environment — Design

**Date:** 2026-07-06
**Status:** Approved

## Goal

QA (and any developer) can bring up a working environment with a single command and **zero manual setup**:

```
docker compose up --build
```

No `.env` file to create, no environment variables to export.

## Scope

In scope:
- PostgreSQL database (latest stable major)
- Spring Boot backend, containerized
- pgAdmin web UI for browsing the database

Out of scope:
- Frontend — the `frontend/` directory is currently empty; deferred to a later task.

## Key Decisions

1. **Zero-setup credentials.** Instead of a `.env` file that must be created,
   `docker-compose.yml` uses inline defaults via `${VAR:-default}` interpolation.
   With nothing set, Compose falls back to dev-only defaults, so the stack starts
   cold. A `.env` file or exported env vars can still override for anyone who wants to.
   These are throwaway local-dev credentials, so no secret is committed.

2. **Pinned PostgreSQL major version.** Use `postgres:18` (latest stable major as of
   2026-07; PG 19 is still in beta). Avoid the `latest` tag, which silently jumps
   major versions and can break the data directory.

3. **PG18 volume path.** PostgreSQL 18 changed `PGDATA` to a version-specific path
   (`/var/lib/postgresql/18/docker`). The persistence volume mounts at the parent
   `/var/lib/postgresql` so data actually persists.

4. **Backend runtime.** The backend is Spring Boot 4.1.0 on Java 25. The Dockerfile
   uses a multi-stage build: `eclipse-temurin:25-jdk` to build (via the project's
   Maven wrapper, pinned to Maven 3.9.16), `eclipse-temurin:25-jre` to run.

5. **DB connection via env.** The backend reaches Postgres over the Compose network
   at host `postgres`. `application.properties` gets datasource properties with a
   `localhost` fallback (so host runs still work), overridden in-container by
   `SPRING_DATASOURCE_*` env vars set in Compose (Spring relaxed binding).

6. **Schema ownership.** Liquibase is on the classpath and owns schema management, so
   Hibernate is set to `spring.jpa.hibernate.ddl-auto=validate` (validate only, no
   auto-DDL).

## Components

### `docker-compose.yml` (project root)

| Service   | Image / Build         | Ports        | Notes |
|-----------|-----------------------|--------------|-------|
| postgres  | `postgres:18`         | `5432:5432`  | inline-default creds, `postgres_data` volume at `/var/lib/postgresql`, `pg_isready` healthcheck |
| backend   | build `./backend`     | `8080:8080`  | `depends_on: postgres` (service_healthy), `SPRING_DATASOURCE_*` env |
| pgadmin   | `dpage/pgadmin4`      | `5050:80`    | inline-default login, `pgadmin_data` volume, `depends_on: postgres` |

Named volumes: `postgres_data`, `pgadmin_data`.

Default credentials (overridable): DB/user/password = `kanban`;
pgAdmin login = `admin@kanban.local` / `admin`.

### `backend/Dockerfile`

Multi-stage:
- **build:** `eclipse-temurin:25-jdk`; copy `.mvn/`, `mvnw`, `pom.xml`; normalize
  `mvnw` line endings (Windows checkout may be CRLF) and mark executable;
  `./mvnw dependency:go-offline`; copy `src`; `./mvnw clean package -DskipTests`.
- **runtime:** `eclipse-temurin:25-jre`; copy the built jar to `app.jar`;
  `EXPOSE 8080`; `ENTRYPOINT ["java","-jar","app.jar"]`.

### `backend/.dockerignore`

Excludes `target/`, IDE metadata, and other non-build inputs to keep the build
context small and cache stable.

### `backend/src/main/resources/application.properties`

Adds:
```
spring.datasource.url=${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/kanban}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME:kanban}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD:kanban}
spring.jpa.hibernate.ddl-auto=validate
```

### `.gitignore`

Add `.env` so an optional local override file is never committed.

## Verification

- `docker compose config` parses without error.
- `docker compose up --build` starts postgres (healthy) → backend → pgadmin.
- Backend reachable at `http://localhost:8080`, pgAdmin at `http://localhost:5050`.
