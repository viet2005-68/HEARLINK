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
├── entity/           # JPA entities + enums (User, Swipe, Match, RefreshToken, Notification)
├── match/            # Versioned match API, business logic and DTOs
├── notification/     # Own notification history + authenticated SSE
├── recommendation/   # Unswiped public profiles + DTOs
├── swipe/            # Like/pass and mutual matching + DTOs
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

## Recommendations and Swipe API v1

Both endpoints require `Authorization: Bearer <access_token>`.

### GET /api/v1/recommendations?limit=20

Returns `{ "items": [{ "id": 17, "name": "Linh", "bio": "Thích leo núi" }] }`.
Excludes the current user and every profile they have already liked or passed.
Incoming likes do not exclude a profile. Results are ordered by user ID.
Empty results return `items: []`.
`limit` defaults to 20; values outside 1–50 or invalid numbers return 400.
Missing/invalid tokens and deleted current users return 401.

### POST /api/v1/swipes

Request: `{ "target_id": 17, "action": "like" }`. Only lowercase `like` and `pass` are accepted.
The swiper ID always comes from the JWT, never the request body.

Response 201 without a match:

```json
{ "swipe_id": 903, "matched": false, "match": null }
```
Response 201 for the second mutual like:

```json
{ "swipe_id": 904, "matched": true, "match": { "id": 55, "with_user_id": 17 } }
```

| Status | Reason / error code |
|---|---|
| 400 | Invalid body/action/target ID (`VALIDATION_ERROR`), self-swipe (`SELF_SWIPE`) |
| 401 | Missing/invalid token (`UNAUTHENTICATED`), deleted caller (`USER_NOT_FOUND`) |
| 404 | Target does not exist (`TARGET_NOT_FOUND`) |
| 409 | Already liked or passed this target (`ALREADY_SWIPED`) |

The service saves the swipe and any resulting match in one READ_COMMITTED transaction.
It locks both user rows in ascending ID order before checking existing swipes.
Concurrent swipes involving the same user wait for the lock. Matches use
`user_a_id < user_b_id`; database unique constraints prevent duplicate pairs.
Cancelling a match keeps both swipes, so neither participant can swipe again.
All swipe writes must use this locking order. Lock contention can be measured in Phase 2.

## Testing the Phase 1 flow

Requires JDK 21 and Maven dependency access. Unit tests without PostgreSQL:

```bash
./mvnw -Dtest=AuthServiceTests,JwtServiceTests,JwtAuthFilterTests,RecommendationServiceTests,SwipeServiceTests test
```

PostgreSQL integration tests create/drop the schema in `heartlink_test` on port 55432.
Use the separate database provided by `docker-compose.test.yml`.

```bash
docker compose -f docker-compose.test.yml up -d --wait
HEARTLINK_DB_TESTS=true ./mvnw -Dtest=Phase1ApiTests test
docker compose -f docker-compose.test.yml down -v
```

The integration suite checks HTTP authentication/validation/JSON, recommendation limits
and exclusions, mutual likes, cancellation without reswiping, concurrent reciprocal and
duplicate swipes, and rollback when match insertion fails. It is skipped unless
`HEARTLINK_DB_TESTS=true`.

Demo seed derives matches directly from reciprocal LIKE rows. For the eight users,
the expected match pairs are `(1,2)`, `(1,3)`, `(1,4)`, `(4,7)`.

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

Returns `204 No Content` when the active match belongs to the user. It soft-cancels the match by setting `status=CANCELLED` and `cancelled_at`; the match row and old swipe records remain. An active match owned by other users returns `403 MATCH_FORBIDDEN`; a missing or cancelled match returns `404 MATCH_NOT_FOUND`. Errors use the shared JSON envelope.

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

## Notification API v1

Both endpoints require `Authorization: Bearer <access_token>` and are scoped to the current
user. There is no public endpoint for creating notifications.

| Endpoint | Success | Errors |
|---|---|---|
| `GET /api/v1/notifications?limit=20&cursor=...` | `200` own history, notification ID descending | `400 VALIDATION_ERROR`, `400 INVALID_CURSOR`, `401` |
| `GET /api/v1/notifications/stream` | `200 text/event-stream`, live events | `401` |

History uses a default limit of 20, accepts 1–50, and returns an opaque, user-bound cursor
for the next page of **older** notifications. Omit the cursor to refresh the latest page.
`next_cursor` is null when no older page remains.

```json
{
  "items": [
    {
      "id": 123,
      "type": "MATCH_CREATED",
      "match_id": 55,
      "created_at": "2026-10-27T09:00:00Z"
    }
  ],
  "next_cursor": null
}
```

Open the SSE connection with a client that supports the Authorization header:

```bash
curl -N -H "Authorization: Bearer YOUR_ACCESS_TOKEN" \
  http://localhost:8080/api/v1/notifications/stream
```

The stream sends a `connected` comment, heartbeat comments every 15 seconds, and events:

```text
id: 123
event: match.created
data: {"notification_id":123,"match_id":55}

```

Multiple devices/tabs for the same user receive their own copy of a live event. The
connection ends at JWT expiry; refresh the token and reconnect. Browser-native EventSource
cannot set a Bearer header: use a streaming fetch or an SSE client with header support.

For initial connection and reconnects, connect the stream first, wait for its `connected`
comment, then refresh history and paginate older pages as needed. Deduplicate live/history
items by notification ID. SSE is live-only and does not automatically replay Last-Event-ID.
Historical MATCH_CREATED notifications remain after unmatching; fetch current matches before
treating a historical event as an active match.

Match creation integration: `SwipeService` validates reciprocal likes under its ordered
user-row locks, then calls `MatchService.createForMutualLike(first, second)`.
It stores the canonical match and one notification for each participant in the same
transaction. `NotificationService.recordMatchCreated` requires an existing transaction;
its SSE event is dispatched only after a successful commit. Rollback leaves neither the
match nor its notifications. Offline users can retrieve history later.

The HTTP swipe flow creates notifications automatically on the second mutual like.
Direct repository/SQL writes bypass this service hook; the seed script explicitly inserts
notification history for its four demo matches (eight notifications total).

Phase 1 uses an in-process SSE broadcaster for one application instance. Delivery is best
effort: there is no durable dispatcher, cross-instance fan-out, delivery acknowledgement,
or `published_at` tracking. History is durable; reliable live dispatch across failures and
multiple instances remains a Phase 2 concern.

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
| `notifications` | One MATCH_CREATED notification per user/match, indexed by `(user_id, id)` |

Constraints enforced by the entities: `users.email` unique, `swipes (swiper_id, target_id)` unique, `matches (user_a_id, user_b_id)` unique, `notifications (user_id, match_id, type)` unique, and foreign keys. PostgreSQL does not currently enforce `swiper_id <> target_id` or `user_a_id < user_b_id`. `SwipeService` rejects self-swipes and locks both users in ascending ID order; `MatchService.createForMutualLike` stores the canonical pair and both notifications in the same transaction. The database match unique constraint alone would permit both `(A, B)` and `(B, A)`, so all match writers must preserve that order.

> Credentials in `docker-compose.yml` / `application*.properties` are dev defaults only — replace them with environment variables or secrets before deploying anywhere real.
