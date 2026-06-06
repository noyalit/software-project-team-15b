<div align="center">

<img src="ticketmaster-ui/src/assets/Ticket4U_logo.jpeg" alt="Ticket4U logo" width="200"/>

# Ticket4U

**A full-stack ticketing platform for events, halls, lotteries and virtual queues.**

Production companies create events, design seating & standing areas, set policies,
and sell tickets through purchases, lotteries and fair virtual queues — while a
role-based permission system (Founder → Owner → Manager) governs who can do what.

[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react&logoColor=black)](https://react.dev/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

</div>

---

## 📑 Table of Contents

- [Overview](#-overview)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Quick Start (Docker)](#-quick-start-docker)
- [Running Locally (without Docker)](#-running-locally-without-docker)
- [Service URLs](#-service-urls)
- [Configuration](#-configuration)
  - [Spring Profiles](#spring-profiles)
  - [The `.env` File](#the-env-file)
- [How Docker Works](#-how-docker-works)
- [Initial-State Seeding](#-initial-state-seeding)
- [Working with the Database](#-working-with-the-database)
- [Testing](#-testing)
- [API Documentation](#-api-documentation)

---

## 🎯 Overview

Ticket4U is a clean, layered Spring Boot application paired with a React single-page
UI. It models the real workflow of a ticketing business:

- **Members & roles** — register, log in (JWT), and hold appointed roles inside a
  company: **Founder**, **Owner**, and event-level **Manager** (with fine-grained
  permissions). A **System Admin** oversees the platform.
- **Companies & events** — open a production company, create events, configure the
  hall as a set of **seating** and **standing areas**, set purchase/discount
  policies, then **publish**.
- **Selling tickets** — direct purchases, **lotteries**, and **virtual queues** that
  admit buyers fairly under load. Payments and ticket supply go through external
  integrations that can run in `fake` (stub) or `real` mode.
- **Notifications & history** — order history and real-time notifications over
  WebSocket (STOMP).

The backend follows a **Domain / Application / Infrastructure / Controller** layout
and can run entirely **in memory** (zero dependencies) or against **PostgreSQL**.

---

## 🛠 Tech Stack

| Layer        | Technologies                                                                 |
|--------------|------------------------------------------------------------------------------|
| **Backend**  | Java 17, Spring Boot 3 (Web MVC, Data JPA, Security, Validation, WebSocket), Spring Retry, JJWT, springdoc OpenAPI |
| **Frontend** | React 18, TypeScript 5, Vite, Tailwind CSS, React Query, Zustand, Axios, STOMP/SockJS |
| **Data**     | PostgreSQL 16 (prod/docker) · H2 in-memory (dev) · Hibernate / JPA           |
| **Tooling**  | Docker Compose, Maven (`mvnw`), JaCoCo, JUnit 5                              |

---

## 📂 Project Structure

```
software-project-team-15b/
├── Ticketmaster/                 # Spring Boot backend (Java 17)
│   ├── src/main/java/.../Ticketmaster/
│   │   ├── Domain/               # Aggregates & business rules (Member, Event, Company, …)
│   │   ├── Application/          # Services / use cases (incl. Initialization seeding)
│   │   ├── Infrastructure/       # Persistence, config, external integrations
│   │   └── Controller/           # REST controllers (User, Event, Company, Lottery, Queue, …)
│   ├── src/main/resources/
│   │   ├── application*.properties      # Base + per-profile config
│   │   └── initial-state*.txt           # Optional startup seed scripts
│   └── Dockerfile
├── ticketmaster-ui/              # React + Vite + TypeScript frontend
│   └── Dockerfile
├── docker-compose.yml            # postgres + backend + ui
├── .env.example                  # Copy to .env and adjust
└── README.md
```

---

## 🚀 Quick Start (Docker)

The fastest way to run the whole stack — Postgres + backend + UI — with one command.

**Prerequisites:** [Docker](https://docs.docker.com/get-docker/) with Compose.

```bash
# 1. Create your local config from the template
cp .env.example .env

# 2. Build and start everything
docker compose up --build
```

That's it. Open the UI at **http://localhost:5173**.

> The Docker stack always runs the **`docker`** Spring profile and persists every
> aggregate to PostgreSQL. Stop it with `Ctrl+C`, or `docker compose down`.

---

## 💻 Running Locally (without Docker)

Run the backend and UI directly on your machine — great for development.

**Prerequisites:** JDK 17, Node.js 18+, and (only for the `docker` profile) a
running PostgreSQL.

### Option A — fully in-memory (no database)

```bash
# Backend (defaults to the inMemory profile: H2 + HashMap repositories)
cd Ticketmaster && ./mvnw spring-boot:run

# UI (separate terminal)
cd ticketmaster-ui && npm install && npm run dev
```

### Option B — local backend against Dockerized Postgres

```bash
# Start just the database
docker compose up -d postgres

# Backend on the docker profile, pointing at localhost
cd Ticketmaster && SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run

# UI
cd ticketmaster-ui && npm run dev
```

---

## 🔗 Service URLs

| Service             | URL                                        | Notes                                |
|---------------------|--------------------------------------------|--------------------------------------|
| **Web UI**          | http://localhost:5173                      | Vite dev server / container          |
| **Backend API**     | http://localhost:8081                      | Spring Boot                          |
| **Swagger UI**      | http://localhost:8081/swagger-ui.html      | Interactive API explorer             |
| **OpenAPI spec**    | http://localhost:8081/v3/api-docs          | Raw OpenAPI JSON                     |
| **PostgreSQL**      | localhost:5432                             | db `ticketmaster`, user/pass `postgres` |

> Ports are configurable via `.env` (`UI_PORT`, `BACKEND_PORT`, `SERVER_PORT`,
> `POSTGRES_PORT`).

---

## ⚙️ Configuration

All runtime configuration lives in **one** root [`.env`](.env.example) file, read by
**both** Docker Compose (natively) and local `mvnw spring-boot:run` (via a custom
`DotenvEnvironmentPostProcessor` that walks up to find it). Real shell environment
variables always override `.env`, e.g. `INIT_RUN=true ./mvnw spring-boot:run`.

### Spring Profiles

Picking a profile is the **only** storage knob — each one wires both the JPA
datasource and `app.storage.mode` automatically.

| `SPRING_PROFILES_ACTIVE` | Backing store                 | `ddl-auto`    | Required env |
|--------------------------|-------------------------------|---------------|--------------|
| **`inMemory`** *(default)* | H2 in-memory + HashMap repos | `create-drop` | none |
| **`docker`**             | PostgreSQL                    | `update`      | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` *(all default to the compose values)* |
| **`cloudsql`** *(future)* | Google Cloud SQL             | `validate`    | `CLOUDSQL_JDBC_URL`, `CLOUDSQL_USER`, `CLOUDSQL_PASSWORD` |

> **Heads up:** the Docker stack pins `SPRING_PROFILES_ACTIVE=docker` in
> `docker-compose.yml`. The `.env` value governs **local** runs only.

### The `.env` File

Copy `.env.example` → `.env` and adjust. Keep your real `.env` out of version
control so secrets stay local — commit only `.env.example`.

| Variable                  | Default                       | Description |
|---------------------------|-------------------------------|-------------|
| `SPRING_PROFILES_ACTIVE`  | `inMemory`                    | Active profile for **local** runs: `inMemory` \| `docker` \| `cloudsql`. |
| `EXTERNAL_MODE`           | `fake`                        | External payment/ticket integrations: `fake` (stubs) \| `real`. |
| `SERVER_PORT`             | `8081`                        | Port the backend binds to (locally and inside the container). |
| `POSTGRES_DB`             | `ticketmaster`                | Database name (used by the `docker` profile). |
| `POSTGRES_USER`           | `postgres`                    | Database user. |
| `POSTGRES_PASSWORD`       | `postgres`                    | Database password. |
| `POSTGRES_HOST`           | `localhost`                   | DB host for **local** `docker`-profile runs (pinned to `postgres` in the stack). |
| `POSTGRES_PORT`           | `5432`                        | Database port. |
| `BACKEND_PORT`            | `8081`                        | Host port mapped to the backend container. |
| `UI_PORT`                 | `5173`                        | Host port mapped to the UI container. |
| `VITE_API_PROXY_TARGET`   | `http://backend:8081`         | UI dev-server proxy target for API calls. |
| `INIT_RUN`                | `false`                       | Opt-in: run the [initial-state seed](#-initial-state-seeding) once at startup. |
| `INIT_STATE_FILE`         | `classpath:initial-state.txt` | Location of the seed file (`classpath:` or `file:`). |

---

## 🐳 How Docker Works

`docker-compose.yml` defines three services that start in dependency order:

```
postgres ──(healthy)──▶ backend ──▶ ui
```

| Service    | Image / Build         | Host Port              | Role |
|------------|-----------------------|------------------------|------|
| `postgres` | `postgres:16-alpine`  | `${POSTGRES_PORT:-5432}` | Database, data persisted in the `postgres_data` named volume. Has a healthcheck so the backend waits until it's ready. |
| `backend`  | `./Ticketmaster`      | `${BACKEND_PORT:-8081}` | Spring Boot API. Pinned to the `docker` profile; `depends_on` postgres being healthy. |
| `ui`       | `./ticketmaster-ui`   | `${UI_PORT:-5173}`     | React/Vite dev server, proxies API calls to the backend. |

Every value is sourced from `.env` with sensible fallbacks, so the same compose
file works out of the box and stays fully overridable.

### Common Docker commands

```bash
docker compose up --build           # build + start the full stack
docker compose up -d postgres       # start only the database (detached)
docker compose logs -f backend      # tail backend logs
docker compose ps                   # list running services
docker compose down                 # stop and remove containers (DATA KEPT)
docker compose down -v              # stop and DROP the database volume (fresh DB)
```

---

## 🌱 Initial-State Seeding

Bring a fresh system to a known state at startup by executing a small **plaintext
script** of use cases — handy for demos, manual testing, and reproducible setups.

**Seeding is opt-in and abort-on-failure:** it runs **only** when `INIT_RUN=true`,
executes once during startup, and if **any** step fails it stops and reports the
exact line — so the app never starts in a half-seeded state.

```bash
# Local
INIT_RUN=true ./mvnw spring-boot:run

# Docker (needs a fresh DB, since it aborts on duplicate data)
docker compose down -v && INIT_RUN=true docker compose up --build
```

Point at a different script with `INIT_STATE_FILE` (e.g.
`INIT_STATE_FILE=file:/path/to/seed.txt`). The default lives at
[`initial-state.txt`](Ticketmaster/src/main/resources/initial-state.txt), and a fully
commented reference is in
[`initial-state.example.txt`](Ticketmaster/src/main/resources/initial-state.example.txt).

### Script format

- One statement per line, **terminated by `;`** — e.g. `login(rina, secret);`
- Arguments are comma-separated; **wrap a value in double quotes** to keep commas or
  spaces: `"Tel Aviv Arena"`.
- Comments run from an unquoted `#` or `//` to end of line.
- Entities are referenced by the **names you used earlier** (usernames, company
  names, event names) — **order matters**.

### Supported operations

| Operation | Arguments |
|-----------|-----------|
| `guest-registration` | `(username, password, birthDate[yyyy-MM-dd])` |
| `login` | `(username, password)` |
| `admin-login` | `(username, password)` |
| `open-production-company` | `(actorUsername, companyName)` — actor becomes the active founder |
| `create-event` | `(actorUsername, companyName, eventName, artist, category, startsAt[ISO-8601], location)` |
| `add-standing-area` | `(actorUsername, eventName, areaName, price, currency, capacity)` |
| `add-seating-area` | `(actorUsername, eventName, areaName, price, currency, rows, seatsPerRow)` |
| `publish-event` | `(actorUsername, eventName)` — requires at least one area |
| `appoint-owner` | `(actorUsername, companyName, targetUsername)` |
| `appoint-founder` | `(actorUsername, companyName, targetUsername)` |
| `appoint-manager` | `(actorUsername, eventName, targetUsername, PERM1\|PERM2\|...)` |
| `create-lottery` | `(actorUsername, companyName, eventName)` |
| `create-event-queue` | `(actorUsername, eventName, capacity, maxAccepted)` |

> **Appointments auto-complete the appointee handshake**, so the target user must be
> **logged in before** being appointed.

**`category`:** `CONCERT` · `SPORTS` · `THEATER` · `CONFERENCE` · `FESTIVAL` · `OTHER`

**Manager permissions:** `MANAGE_EVENTS` · `CONFIGURE_HALLS_AND_SEATS` ·
`UPDATE_EVENT_MAP` · `DEFINE_PURCHASE_POLICY` · `DEFINE_DISCOUNT_POLICY` ·
`HANDLE_INQUIRIES` · `VIEW_PURCHASE_AND_ORDER_HISTORY` · `GENERATE_SALES_REPORTS`

<details>
<summary><strong>Example seed script</strong></summary>

```text
guest-registration(moshe, Moshe123!, 1990-01-15);
guest-registration(rina, Rina4567!, 1985-05-20);

login(rina, Rina4567!);
login(moshe, Moshe123!);

open-production-company(rina, "Rina Productions");

create-event(rina, "Rina Productions", SummerShow, "The Band", CONCERT, 2026-09-01T20:00:00Z, "Tel Aviv Arena");
add-standing-area(rina, SummerShow, "General Admission", 150.00, USD, 500);
publish-event(rina, SummerShow);

appoint-manager(rina, SummerShow, moshe, MANAGE_EVENTS|HANDLE_INQUIRIES);
```
</details>

---

## 🗄 Working with the Database

The `postgres` service stores data in the named volume `postgres_data`, and the
`docker` profile uses `ddl-auto=update` — so **data survives** `docker compose down`
and `docker compose up --build`.

**Start from a clean database:**

```bash
docker compose down -v          # stop everything and drop the volume
docker compose up --build       # fresh schema + empty tables
```

**Clear data while keeping the schema:**

```bash
docker compose exec postgres psql -U postgres -d ticketmaster \
  -c "TRUNCATE members, companies, lottery, virtual_queue, virtual_queue_entries, virtual_queue_access_map, lottery_entries, lottery_winners, active_orders, active_order_seats, order_history, order_history_tickets, system_admins, notifications, roles, manager_permissions, event, event_area, seat RESTART IDENTITY CASCADE;"
```

---

## 🧪 Testing

```bash
# Backend unit + slice tests (no database required)
cd Ticketmaster && ./mvnw test
```

**Live-database integration tests** verify every aggregate against a real Postgres —
round-trip saves and raw-SQL row counts across all tables, plus failure classes that
only surface against a real DB (connection-pool leaks, DB-enforced uniqueness,
orphaned element-collection rows, transaction-rollback isolation, idempotent saves):

```bash
docker compose up -d postgres
cd Ticketmaster && ./mvnw test -Dtest='Postgres*IT' -Dpostgres.it=true
```

These are silently **skipped** when `-Dpostgres.it` is unset, so a plain
`./mvnw test` keeps working without a database.

---

## 📚 API Documentation

Interactive API docs (springdoc OpenAPI) are served by the running backend:

- **Swagger UI:** http://localhost:8081/swagger-ui.html
- **OpenAPI JSON:** http://localhost:8081/v3/api-docs

Use Swagger UI to explore endpoints, see request/response schemas, and try calls
directly from the browser.

---

## 🎓 About the Project

This project was developed by **Group 15B** as part of a course at
**Ben-Gurion University of the Negev (BGU)**.

| | |
|---|---|
| **Course** | Workshop on Software Engineering Project |
| **Course number** | 20215141 |
| **Type** | Mandatory |
| **Credits** | 3 |
| **Lecturer** | Dr. Shahaf Shperberg |

### 👥 Contributors

Group 15B — the team behind Ticket4U:

- [@noyalit](https://github.com/noyalit)
- [Gabriel Rozental](https://github.com/Grozental11)
- [Or Malky](https://github.com/OrMalky)
- [Elad Alfi](https://github.com/EladAlfi)
- [Tomer Gelberg](https://github.com/TomerGelbergBGU)
- [Kfir Mizrahi](https://github.com/KfirMiz)
- [@shirahad](https://github.com/shirahad)
- [@oriakaster](https://github.com/oriakaster)

---

<div align="center">
<sub>Built by Group 15B · Ben-Gurion University of the Negev</sub>
</div>
