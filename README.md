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
        ├── main/java/com/vippro/heartlink/   # see Package structure
        ├── main/resources/
        │   ├── application.properties
        │   └── application-docker.properties
        └── test/java/com/vippro/heartlink/   # mirrors main packages
```

All commands below are run from `heartlink/`.

## Package structure

The existing JPA schema is in `entity/`. Endpoint code is organized by capability, with repository and shared error handling packages:

```text
src/main/java/com/vippro/heartlink/
├── HeartlinkApplication.java
├── common/error/     # Shared { error: { code, message } } responses
├── entity/           # JPA entities + enums (User, Swipe, Match)
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

## Match API v1

The match endpoints use the existing `matches` table and schema. For now, the current user is supplied through `X-User-Id` because JWT authentication has not been implemented yet. This header is a development integration point, not secure authentication.

```http
GET /api/v1/matches?limit=20&offset=0
X-User-Id: 1
```

Returns only active matches involving that user. `limit` defaults to 20 and is capped at 50; `offset` defaults to 0. Each result exposes the other user's public profile fields, plus status and creation time.

```http
DELETE /api/v1/matches/1
X-User-Id: 1
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

Constraints enforced by the entities: `users.email` unique, `swipes (swiper_id, target_id)` unique, `matches (user_a_id, user_b_id)` unique. The `swiper_id <> target_id` and `user_a_id < user_b_id` rules are business rules handled in the service layer.

> Credentials in `docker-compose.yml` / `application*.properties` are dev defaults only — replace them with environment variables or secrets before deploying anywhere real.
