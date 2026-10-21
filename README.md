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
    ├── docker-compose.yml    # postgres + app
    ├── seed/seed.sql         # demo data
    ├── Dockerfile            # packages the pre-built JAR
    ├── pom.xml
    ├── mvnw
    └── src/main/
        ├── java/com/vippro/heartlink/
        │   └── entity/       # User, Swipe, Match (+ enums)
        └── resources/
            ├── application.properties
            └── application-docker.properties
```

All commands below are run from `heartlink/`.

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
