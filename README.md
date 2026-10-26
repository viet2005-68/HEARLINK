# Heartlink

Dating backend — Phase 1. Spring Boot 4.1 (Java 21) + PostgreSQL 16, single deployable app and one database.

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1 — WebMVC, Data JPA, Validation |
| Database | PostgreSQL 16 (Docker) |
| ORM | Hibernate / JPA |
| API docs | springdoc-openapi (Swagger UI) |
| Password hashing | BCrypt (`spring-security-crypto`) |

## Requirements

- JDK 21
- Docker + Docker Compose v2

## Project layout

```text
love_love/
├── README.md
└── heartlink/
    ├── docker-compose.yml     # postgres + app
    ├── Dockerfile             # packages the pre-built JAR
    ├── .dockerignore
    ├── .gitignore
    ├── pom.xml
    ├── mvnw / mvnw.cmd
    ├── seed/seed.sql          # demo data
    └── src/
        ├── main/java/com/example/heartlink/   # see Package structure
        ├── main/resources/
        │   ├── application.properties
        │   └── application-docker.properties
        └── test/java/com/example/heartlink/   # mirrors main packages
```

All commands below are run from `heartlink/`.

## Package structure

The existing JPA schema is in `entity/`. Endpoint code is organized by capability, with repository and shared error handling packages:

```text
src/main/java/com/example/heartlink/
├── HeartlinkApplication.java
├── auth/             # Register / login / refresh / logout + DTOs
├── common/error/     # Shared { error: { code, message } } responses
├── common/security/  # JWT issuing/verification + shared auth filter
├── entity/           # JPA entities + enums (User, Swipe, Match, RefreshToken)
├── match/            # Versioned match API, business logic and DTOs
├── repository/       # Spring Data JPA interfaces
└── ...               # Other business modules as they are implemented
```

Each module keeps its HTTP controller, service and DTOs close to its domain; JPA entities stay internal and are not returned directly.

## Run

### Option 1 — Everything in Docker

```bash
# 1. Build the JAR (the Docker image packages this file)
./mvnw clean package -DskipTests

# 2. Build the image and start postgres + app
docker compose up -d --build

# 3. Load demo data (only after the app has created the tables)
docker compose exec -T postgres psql -U postgres -d heartlink < seed/seed.sql
```

### Option 2 — Database in Docker, app in your IDE

```bash
docker compose up -d postgres
./mvnw spring-boot:run
```

`application.properties` connects to `localhost:5432`. The `docker` profile (`application-docker.properties`) is activated by Compose and connects to the `postgres` service name instead.

## URLs

| What | URL |
|---|---|
| API | <http://localhost:8080> |
| Swagger UI | <http://localhost:8080/swagger-ui/index.html> |
| PostgreSQL | `localhost:5432` — db `heartlink`, user `postgres`, password `123456` |

## Auth API v1

All endpoints under `/api/v1/**` except `/api/v1/auth/**` require `Authorization: Bearer <access_token>`. The check is done once in `common/security/JwtAuthFilter`; controllers never parse tokens themselves and read the current user with `@RequestAttribute(CurrentUser.ID) Long userId`. Missing/invalid/expired tokens get `401 {"error":{"code":"UNAUTHENTICATED",...}}`.

Every seeded user has password `123456`.

```http
POST /api/v1/auth/login
{ "email": "anna@example.com", "password": "123456" }
```

```json
{ "access_token": "<jwt>", "token_type": "bearer", "expires_in": 3600, "refresh_token": "<opaque>" }
```

Wrong email or password → `401 INVALID_CREDENTIALS`.

| Endpoint | Body | Success | Errors |
|---|---|---|---|
| `POST /api/v1/auth/register` | `{ email, password (6-72), name, bio? }` | `201 { id, email, name, bio }` | `400`, `409 EMAIL_TAKEN` |
| `POST /api/v1/auth/refresh` | `{ "refresh_token": "..." }` | `200` new access + refresh token (old refresh token is revoked) | `401 INVALID_REFRESH_TOKEN` |
| `POST /api/v1/auth/logout` | `{ "refresh_token": "..." }` | `204` (idempotent) | `400` |

Access tokens are stateless HS256 JWTs and simply expire after `expires_in`; logout revokes the refresh token so no new access tokens can be obtained. Refresh tokens are stored only as SHA-256 hashes in `refresh_tokens` and live 7 days. Config: `app.jwt.secret` (env `JWT_SECRET`, ≥ 32 bytes), `app.jwt.expires-in`, `app.jwt.refresh-expires-in`.

## Match API v1

The match endpoints use the existing `matches` table and schema and require a Bearer token (see Auth API).

```http
GET /api/v1/matches?limit=20&offset=0
Authorization: Bearer <access_token>
```

Returns only active matches involving that user. `limit` defaults to 20 and is capped at 50; `offset` defaults to 0. Each result exposes the other user's public profile fields, plus status and creation time.

```http
DELETE /api/v1/matches/1
Authorization: Bearer <access_token>
```

Returns `204 No Content` when the active match belongs to the user. It soft-cancels the match by setting `status=CANCELLED` and `cancelled_at`; the match row and old swipe records remain. Missing/inactive/non-owned matches return a shared JSON error envelope.

Example response:

```json
{
  "items": [
    {
      "id": 42,
      "user": { "id": 2, "name": "Binh", "bio": "Yêu bóng đá" },
      "status": "ACTIVE",
      "createdAt": "2026-10-21T10:00:00Z"
    }
  ],
  "limit": 20,
  "offset": 0,
  "total": 1
}
```

## Useful commands

```bash
docker compose logs -f app        # app logs
docker compose logs -f postgres   # database logs
docker compose ps                 # container status
docker compose exec postgres psql -U postgres -d heartlink   # psql shell
docker compose down               # stop, keep data
docker compose down -v            # stop and delete data
./mvnw clean                      # remove build output
```

## Database

Tables are created by Hibernate on startup (`spring.jpa.hibernate.ddl-auto=update`), so no manual SQL is needed.

| Table | Purpose |
|---|---|
| `users` | Account + profile, `email` unique |
| `swipes` | One row per swiper/target pair, `action` = `LIKE` / `PASS` |
| `matches` | Matched pair (`user_a_id` < `user_b_id`), `status` = `ACTIVE` / `CANCELLED` |
| `refresh_tokens` | Hashed refresh tokens with `expires_at` / `revoked_at` |

Constraints enforced by the entities: `users.email` unique, `swipes (swiper_id, target_id)` unique, `matches (user_a_id, user_b_id)` unique. The `swiper_id <> target_id` and `user_a_id < user_b_id` rules are business rules handled in the service layer.

> Credentials in `docker-compose.yml` / `application*.properties` are dev defaults only — replace them with environment variables or secrets before deploying anywhere real.
