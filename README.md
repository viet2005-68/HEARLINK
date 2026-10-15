# HeartLink project structure

Spring Boot modular monolith, grouped by business capability. Keep one deployable application and one PostgreSQL database for Phase 1.

```text
src/main/java/com/example/heartlink/
├── HeartlinkApplication.java
├── common/
│   ├── config/             # Security, OpenAPI and application configuration
│   ├── error/              # Shared error response and global exception handling
│   │   └── exception/
│   └── web/                # Cross-cutting endpoints such as /health
├── auth/                   # POST /auth/login, JWT issuance and authentication
│   ├── dto/
│   └── security/
├── user/                   # User profile model, persistence and profile DTOs
│   └── dto/
├── recommendation/         # GET /recommendations and candidate selection
│   └── dto/
├── swipe/                  # POST /swipes and swipe persistence
│   └── dto/
└── match/                  # GET /matches and DELETE /matches/{id}
    └── dto/

src/main/resources/
├── application.properties
└── db/migration/           # Flyway schema and seed data migrations

src/test/java/com/example/heartlink/
├── auth/
├── recommendation/
├── swipe/
└── match/
```

## Module conventions

- Each business module owns its controller, service, repository, entity and request/response DTOs.
- Keep swipe creation and reciprocal-like/match creation in one transactional service operation.
- Use database unique constraints for duplicate swipes and matches; unmatching changes match state and preserves swipe history.
- Put the shared `{ "error": { "code", "message" } }` response and exception mapping under `common/error`.
- Seed demo accounts through versioned database migrations or a dedicated seed profile; never store plain-text passwords.
- `/users/me`, swipe history, registration, refresh/logout and profile editing are outside the initial required scope and can be added to their owning modules later.
