# Inventory Billing System

REST API for a grocery store: inventory, supplier purchases and sales with basic billing.

## Features

- JWT authentication with `ADMIN` and `SELLER` roles.
- Product catalog with categories, per-product VAT (0% / 16%), barcode lookup and search.
- Supplier purchases with concurrency-safe stock and idempotent requests.
- Customers, with SAT's walk-in customer ("PUBLICO EN GENERAL") as the default.
- Sales with server-side prices and per-line VAT, sequential sale numbers, a required `Idempotency-Key` and
  atomic cancellation that returns stock. Stock never goes negative, even when sellers race for the last unit.
- Manual stock adjustments with a required reason (optionally idempotent), and a per-product stock ledger
  of every change.
- Reports: sales by period (totals, payment methods, sellers, top products, daily breakdown in the store's
  time zone) and a low-stock reorder list. Sellers only see their own sales.

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

## Tests

```bash
./mvnw verify
```

## Environment variables

| Variable | Description |
|---|---|
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | PostgreSQL connection (production) |
| `JWT_SECRET` | Base64, at least 32 bytes (`openssl rand -base64 32`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Initial admin, created if none exists |
