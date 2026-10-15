# Inventory Billing System

REST API for a grocery store: inventory, supplier purchases and sales with basic billing.

## Features

- JWT authentication with `ADMIN` and `SELLER` roles.
- Product catalog with categories, per-product VAT (0% / 16%), barcode lookup and search.
- Supplier purchases with concurrency-safe stock and idempotent requests.
- Customers, with SAT's walk-in customer ("PUBLICO EN GENERAL") as the default.
- Sales with server-side prices and per-line VAT, sequential sale numbers, a required `Idempotency-Key` and
  atomic cancellation that returns stock. Stock never goes negative, even when sellers race for the last unit.
- Cash drawers: each seller opens the day's drawer with an opening float (required to sell) and closes it
  with the counted cash, recording the expected cash and the surplus or shortage.
- Manual stock adjustments with a required reason (optionally idempotent), and a per-product stock ledger
  of every change.
- Reports: sales by period (totals, payment methods, sellers, top products, daily breakdown in the store's
  time zone), a daily cash closing by payment method with the day's drawers and a low-stock reorder list. Sellers only see their own
  sales.

Database design: [docs/data-model.md](docs/data-model.md).

## Stack

Java 21 · Spring Boot 4 · Spring Security · PostgreSQL 17 · Flyway · Docker · Testcontainers

## Run locally

Requires JDK 21 and Docker.

1. Copy `.env.example` to `.env` and fill in the values.
2. Run:

```bash
./mvnw spring-boot:run
```

API docs: http://localhost:8080/swagger-ui.html

## Run with Docker

The image packages the app only (tests run in CI). Against any PostgreSQL:

```bash
docker build -t inventory-billing-system .
docker run -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod -e DB_HOST=... -e DB_NAME=... -e DB_USER=... -e DB_PASSWORD=... -e JWT_SECRET=... -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... inventory-billing-system
```

## Deploy (Render)

`render.yaml` is a Render Blueprint: the API (Docker, free plan) plus a private PostgreSQL 17.

1. In Render: **New > Blueprint**, pick this repository.
2. Fill in `ADMIN_EMAIL` and `ADMIN_PASSWORD` when asked; `JWT_SECRET` is generated and the database
   variables are wired automatically.
3. Every push to `main` deploys once CI passes; `/actuator/health` (database included) gates each deploy.

On the free plan the service sleeps after a few idle minutes (the first request takes about a minute)
and the free database expires after a limited time; Flyway recreates the schema on a new one.

## Tests

```bash
./mvnw verify
```

## Environment variables

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` in containers (reads the variables below) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection (`prod`; port defaults to 5432) |
| `PORT` | HTTP port (`prod`; defaults to 8080, Render sets it) |
| `JWT_SECRET` | Base64, at least 32 bytes (`openssl rand -base64 32`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Initial admin, created if none exists |
