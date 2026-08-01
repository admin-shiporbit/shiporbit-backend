# ShipOrbit — Backend

Backend service for **ShipOrbit**, a transparency-first shipping aggregator for
Indian D2C e-commerce sellers. It exposes the APIs that power seller
onboarding, order and shipment management, courier rate comparison, wallet and
billing, tracking, and related operations.

> **Status:** Early development. The authentication module is being built first;
> other modules follow. See [Roadmap](#roadmap) for what's implemented vs planned.

---

## What ShipOrbit does

ShipOrbit helps D2C sellers ship smarter by comparing courier rates
transparently — showing the real cost breakdown rather than hidden markups — and
managing the full shipping lifecycle: orders, shipments, tracking, returns,
NDR, weight-discrepancy disputes, wallet/COD remittances, and analytics.

The frontend (separate Next.js application) is already built as a prototype; this
backend is being developed module by module to serve it.

---

## Tech stack

| Layer            | Technology                                  |
|------------------|---------------------------------------------|
| Language         | Java 21                                      |
| Framework        | Spring Boot 3.x (Spring Web, Security, Data JPA, Validation) |
| Build            | Gradle                                       |
| Database (dev)   | H2 (in-memory)                               |
| Database (target)| PostgreSQL (Neon)                            |
| Auth             | Spring Security 6 + JWT (Bearer tokens)      |
| Password hashing | BCrypt                                        |

Planned infrastructure for later modules: Kafka (Confluent), Redis (Upstash),
Cloudflare R2.

---

## Prerequisites

- **JDK 21** (e.g. via SDKMAN: `sdk install java 21-tem`)
- **Gradle** — not required to install separately; use the bundled wrapper
  (`./gradlew`)
- Git

---

## Getting started

Clone and enter the project:

```bash
git clone <your-repo-url>
cd shiporbit-backend/backend
```

Run the application:

```bash
./gradlew bootRun
```

Or run `BackendApplication` from your IDE.

The app starts on **http://localhost:8080**.

### H2 database console (dev)

While the app is running, open the H2 console to inspect data:

- URL: **http://localhost:8080/h2-console**
- JDBC URL: `jdbc:h2:mem:shiporbit`
- Username: `SA`
- Password: *(leave blank)*

> The in-memory database is recreated on every restart. Schema and seed data are
> loaded from `schema.sql` and `data.sql` on startup.

---

## Configuration

Configuration lives in `src/main/resources/application.yml`. The committed file
contains **safe dev defaults only** (H2, blank password).

**Never commit real credentials.** When moving to Postgres/Neon or adding other
services, keep secrets in an untracked `application-local.yml` or environment
variables (see `.gitignore`), and load them via env vars — e.g.:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

---

## Database conventions

- **Object naming prefix:** all DB objects use a `SO_` product prefix
  (`SO_USERS`, `SO_ROLES`), with type markers for non-tables (e.g. `SO_V_` views,
  `SO_SP_` procedures, `SO_FN_` functions). Constraints follow
  `SO_<type>_<table>_<column>` (e.g. `SO_FK_USERS_ROLE_ID`, `SO_UQ_USERS_EMAIL`).
- **Schema management:** `schema.sql` is the source of truth for structure;
  `data.sql` seeds constant reference data (roles). Hibernate does not manage the
  schema (`ddl-auto: none`).
- **Identity model:** a single `SO_USERS` table holds all user identities,
  differentiated by a foreign key to `SO_ROLES`. Per-role profile data lives in
  separate tables (added per module), keeping the identity table identity-only.

---

## Project structure

```
com.shiporbit.backend
├── config        # Spring configuration (SecurityConfig, beans)
├── controller    # REST controllers (HTTP layer)
├── service       # Business logic
├── repository    # Spring Data JPA repositories
├── entity        # JPA entities (Users, Role, ...)
├── dto           # Request/response records
└── exception     # Custom exceptions + global handler
```

---

## API overview (auth module)

Base path: `/api/v1/auth`

| Method | Endpoint   | Description                    | Auth |
|--------|------------|-------------------------------|------|
| POST   | `/signup`  | Register a new seller account | No   |
| POST   | `/login`   | Authenticate, receive tokens  | No   |
| POST   | `/refresh` | Exchange refresh for access   | No   |
| POST   | `/logout`  | Revoke refresh token          | No   |
| GET    | `/me`      | Current authenticated user    | Yes  |

> Only `/signup` is in active development at present; the rest are planned as the
> auth module progresses.

### Example: signup

```bash
curl -X POST http://localhost:8080/api/v1/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
        "fullName": "Jane Seller",
        "email": "jane@brand.in",
        "password": "secret123"
      }'
```

Returns `201 Created` with the created user's details (never the password hash).
New public signups are always created with the **SELLER** role; privileged roles
are provisioned separately, not via public signup.

---

## Roadmap

**Auth module (in progress)**
- [x] Identity model — `Users` / `Role` entities, schema + seeded roles
- [x] Security configuration (stateless, public auth routes)
- [ ] Signup flow
- [ ] JWT token engine
- [ ] Login flow
- [ ] JWT request filter + protected routes
- [ ] Refresh + logout
- [ ] Role-based authorization

**Subsequent modules (planned)**
Seller/Account, Orders/Shipments, Products, Rate Engine, Serviceability,
Wallet/Billing, Tracking, Channels, Returns, NDR, Weight Discrepancies,
Reporting, and read-only dashboards (Fulfillment, Cross-border, Checkout,
Capital, Plans, Branded Tracking, API Keys).

---

## License

Proprietary — © ShipOrbit. All rights reserved.