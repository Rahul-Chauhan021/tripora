# Tripora — Registration & Email OTP Verification

This document covers running the **registration + email OTP verification**
module of Tripora (Spring Boot backend + React frontend).

## Prerequisites

- Java 21+
- Node.js 18+
- A running PostgreSQL server with an existing database (nothing is created,
  dropped or reset by this application — tables are managed with
  `spring.jpa.hibernate.ddl-auto=update`, which only adds/updates the tables
  owned by this app and preserves all existing data).

## 1. Backend (`tripora_Backend/`)

All configuration lives in **`tripora_Backend/.env`** (git-ignored — never
commit real secrets). Real OS environment variables, if set, take
precedence over the file.

| Variable         | Required | Default              | Purpose                                   |
| ---------------- | -------- | -------------------- | ----------------------------------------- |
| `DB_HOST`        | no       | `localhost`          | PostgreSQL host                           |
| `DB_PORT`        | no       | `5432`               | PostgreSQL port                           |
| `DB_NAME`        | no       | `tripora_db`         | Database name                             |
| `DB_USERNAME`    | no       | `tripora_user`       | Database user                             |
| `DB_PASSWORD`    | **yes**  | *(empty)*            | Database password                         |
| `MAIL_HOST`      | **yes*** | *(empty)*            | SMTP host (e.g. `smtp.gmail.com`)         |
| `MAIL_PORT`      | no       | `587`                | SMTP port (587 = STARTTLS)                |
| `MAIL_USERNAME`  | **yes*** | *(empty)*            | SMTP login                                |
| `MAIL_PASSWORD`  | **yes*** | *(empty)*            | SMTP password / app password              |
| `MAIL_FROM`      | no       | `no-reply@tripora.app` | From address for OTP emails             |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Comma-separated allowed frontend origins |

\* If SMTP is not configured, the API still starts but returns a clear
**503 configuration error** for registration/resend instead of pretending an
email was delivered.

Open `tripora_Backend/.env` and fill in `DB_PASSWORD` and the `MAIL_*`
values. Then start the backend:

### Windows (PowerShell)

```powershell
cd tripora_Backend
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
cd tripora_Backend
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`.

> Gmail note: with 2-Step Verification enabled you must create an
> [App Password](https://myaccount.google.com/apppasswords) and use it as
> `MAIL_PASSWORD`.

## 2. Frontend (`client/`)

The frontend needs **no configuration**: during development the Vite dev
server proxies `/api/*` requests to `http://localhost:8080` (see
`client/vite.config.js`). In production, serve the built frontend behind the
same origin as the API (or add a reverse proxy for `/api`).

```bash
cd client
npm install
npm run dev        # development server on http://localhost:5173
npm run build      # production build into client/dist
```

## 3. Endpoints

| Method | Path                    | Purpose                                  |
| ------ | ----------------------- | ---------------------------------------- |
| POST   | `/api/auth/register`    | Create pending account + send OTP email  |
| POST   | `/api/auth/verify-otp`  | Verify OTP, activate account             |
| POST   | `/api/auth/resend-otp`  | Resend OTP (60s cooldown, rate limited)  |

Example register request:

```json
{
  "email": "user@example.com",
  "password": "StrongPassword123!",
  "confirmPassword": "StrongPassword123!"
}
```

## 4. Security properties

- Passwords are BCrypt-hashed; raw passwords are never stored or logged.
- OTPs are cryptographically secure 6-digit values; only their SHA-256 hash
  is persisted. OTPs expire after 5 minutes, are single-use, and allow at
  most 5 incorrect attempts (enforced with atomic DB updates).
- Resend has a 60-second cooldown plus an hourly rate limit.
- The email is sent **outside** the DB transaction; if delivery fails the
  pending registration is rolled back so the account is never stuck.
- Error responses never reveal whether an OTP was "close" and never echo
  secrets.

## 5. Running tests

```bash
cd tripora_Backend
.\mvnw.cmd test        # Windows (./mvnw test on macOS/Linux)
```

Tests use JUnit 5 + Mockito with an in-memory H2 database and a mocked mail
service — no live SMTP or PostgreSQL is required.
