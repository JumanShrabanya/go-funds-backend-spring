# Go Funds — Backend

Spring Boot API for the Go Funds app: a mutual fund catalogue synced from AMFI,
email-OTP authentication, and an investment planner that uses Gemini to pick funds
and split your monthly SIP across them.

Built as a rewrite of the original NestJS backend. The REST contract is unchanged,
so the existing frontend works against it as-is.

- **Java 21** · Spring Boot 4.1.1 · Maven
- Spring Security (JWT, stateless) · Spring Data JPA · Liquibase · PostgreSQL
- Spring AI 2.0.1 on Gemini (`gemini-2.5-flash` by default)
- springdoc-openapi for Swagger UI

There are two longer documents if you want the reasoning behind the code:
[`src/main/resources/static/agent.md`](src/main/resources/static/agent.md) covers the
whole migration, and [`planner.md`](planner.md) covers the planner in detail.
[`API.md`](API.md) is the endpoint-by-endpoint reference for the frontend.

---

## Running it

You need **JDK 21**. If the build dies with `error: release version 21 not supported`,
your `JAVA_HOME` is still pointing at an older JDK.

```bash
export DATABASE_URL="jdbc:postgresql://localhost:5432/gofunds"
export DB_USER=postgres
export DB_PASSWORD=postgres
export RSA_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\nMIIE...\n-----END PRIVATE KEY-----"
export SMTP_USER=you@gmail.com
export SMTP_PASS=your-app-password
export GEMINI_API_KEY=...

./mvnw spring-boot:run
```

It serves on `http://localhost:8080`. `GET /api/v1/health` is the liveness probe.

### Environment variables

| Variable | Required | Notes |
|---|---|---|
| `DATABASE_URL` | yes | Full JDBC URL. Nothing has a default, so the app will not boot without it. |
| `DB_USER` / `DB_PASSWORD` | yes | Postgres credentials. |
| `RSA_PRIVATE_KEY` | yes | PKCS#8 PEM. `\n` escapes are fine, so it can be one line. |
| `SMTP_USER` / `SMTP_PASS` | yes | Gmail address + [app password](https://support.google.com/accounts/answer/185833), not your account password. Used for the OTP emails. |
| `GEMINI_API_KEY` | yes | Without it the app boots fine, but `POST /api/v1/planner/generate` returns **503**. Reading existing plans still works. |
| `GEMINI_MODEL` | no | Defaults to `gemini-2.5-flash`. |
| `JWT_ACCESS_SECRET` / `JWT_REFRESH_SECRET` | prod | Dev-only placeholders are baked in. Override these. |
| `CORS_ORIGINS` | prod | Comma-separated. Defaults to `localhost:3000` and `localhost:5173`. |
| `FUNDS_SYNC_ON_STARTUP` | no | `true` seeds the fund catalogue on boot instead of waiting for the 00:30 IST cron. |
| `LOG_OTP` | no | `true` prints every OTP to the log. Handy locally so you don't need a real mailbox — but the OTP is a live credential for 15 minutes, so leave it off anywhere you don't control the log. |
| `PLANNER_CATALOG_SIZE` | no | How many funds go to the model per request. Defaults to 30. |
| `AMFI_NAV_URL` / `AMFI_SYNC_CRON` | no | Override the NAV feed or the sync schedule. |
| `PORT` | no | Defaults to 8080. |

### Seeding the fund catalogue

The database starts empty and `POST /planner/generate` returns **409** until it isn't.
A scheduled sync runs at 00:30 IST, which is no use if you just want to try things
out. For local work:

```bash
FUNDS_SYNC_ON_STARTUP=true ./mvnw spring-boot:run
```

That pulls ~9,300 schemes from AMFI's `NAVAll.txt` and classifies each one. The
upsert is keyed on `scheme_code`, so it is safe to repeat, but leave it off in
production.

---

## API docs

Swagger UI is at <http://localhost:8080/swagger-ui.html> and needs no token. Hit
**Authorize**, paste an access token from `/api/v1/auth/login`, and the protected
endpoints become callable from the browser.

The raw document is at `/v3/api-docs`.

[`API.md`](API.md) is the same surface as a readable markdown reference — every
endpoint, request field, response shape and enum, with examples. It is maintained by
hand, so update it when you change a controller.

## Endpoints

| Method | Path | Auth | |
|---|---|---|---|
| POST | `/api/v1/auth/register` | — | Create account, mail an OTP, return tokens |
| POST | `/api/v1/auth/login` | — | RSA-decrypt the password, bcrypt verify, return tokens |
| POST | `/api/v1/auth/refresh` | — | Rotate the refresh token |
| POST | `/api/v1/auth/logout` | ✓ | Revoke all refresh tokens |
| GET | `/api/v1/auth/me` | ✓ | Current profile |
| POST | `/api/v1/auth/verify-email` | — | Consume the verification OTP |
| POST | `/api/v1/auth/resend-otp` | — | Re-issue an OTP |
| POST | `/api/v1/auth/forgot-password` | — | Send a reset code |
| POST | `/api/v1/auth/reset-password` | — | Consume the reset OTP, set a new password |
| GET | `/api/v1/funds` | ✓ | Filter by `category` / `riskLevel` / `search`, paged |
| GET | `/api/v1/funds/{schemeCode}` | ✓ | One fund by AMFI scheme code |
| POST | `/api/v1/planner/generate` | ✓ | Ask Gemini for a plan — slow, one LLM round trip |
| GET | `/api/v1/planner/plans` | ✓ | Your plans, newest first |
| GET | `/api/v1/planner/plans/{id}` | ✓ | One plan |
| DELETE | `/api/v1/planner/plans/{id}` | ✓ | Soft delete |
| GET | `/api/v1/planner/stats` | ✓ | Dashboard totals |
| GET | `/api/v1/health` | — | Liveness |

Every response is wrapped in the same envelope:

```json
{ "success": true, "message": "Success", "data": { }, "timestamp": "2026-01-01T00:00:00Z" }
```

### Two things that will trip you up

**Passwords are RSA-encrypted on the way in.** `/auth/register`, `/auth/login` and
`/auth/reset-password` all expect Base64 of a ciphertext produced with the server's
public key using `RSA/ECB/PKCS1Padding`. The server only holds the private key and
does not serve the public key, so the frontend has to bring its own copy. Whatever
comes out of the RSA layer is bcrypt-hashed before it touches the database.

**Return rates are usually `null`.** AMFI publishes current NAV, not return history,
so `returnRate1Year` / `3Year` / `5Year` on a fund are `null` unless something else
filled them in. Don't read that as zero.

---

## Tests

```bash
./mvnw test
```

123 tests, no database needed — the funds, planner, auth and security tests are plain
JUnit and Mockito.

The exception is `GofundsBackendApplicationTests#contextLoads`, which boots the real
application context and therefore needs working Postgres credentials. Without them it
fails in Liquibase with `'url' must start with "jdbc"`. That is expected on a machine
with no `DATABASE_URL`, not a real failure.

---

## Layout

```
auth/      registration, login, OTP, password reset
funds/     AMFI sync, the catalogue, keyword classifier
planner/   Gemini recommendation engine + SIP maths
mail/      verification and reset emails
common/    ApiResponse envelope, JWT, RSA, exception handling
domain/    JPA entities, enums, repositories
config/    SecurityConfig, CORS, OpenAPI
```

Schema changes go in `src/main/resources/db/migration/` as a new `V3__*.sql`; the
changelog picks it up on its own. Hibernate only validates the mapping
(`ddl-auto=validate`) — Liquibase owns the schema.
