# Inventory Billing System

REST API for a grocery store: inventory, supplier purchases and sales with basic billing.

## Features

- JWT authentication with `ADMIN`, `SUPERVISOR` and `SELLER` roles.
- Product catalog with categories, per-product VAT (0% / 16%), barcode lookup, search and product photos
  (PNG, JPEG, WebP or GIF, checked by content; Cloudinary in production, a local folder in development).
- Supplier purchases with concurrency-safe stock and idempotent requests.
- Customers, with SAT's walk-in customer ("PUBLICO EN GENERAL") as the default.
- Sales with server-side prices and per-line VAT, sequential sale numbers, a required `Idempotency-Key` and
  atomic cancellation that returns stock. Stock never goes negative, even when sellers race for the last unit.
- Cash drawers: a seller opens the day's drawer with an opening float (required to sell), or a manager opens
  one for a seller or a shared one for several; its responsible closes it with the counted cash, recording
  the expected cash and the surplus or shortage. Managers have no drawer and don't sell.
- Manual stock adjustments with a required reason (optionally idempotent), and a stock ledger of every
  change, per product or by type across the store.
- Reports: sales by period (totals, payment methods, sellers, top products, daily breakdown in the store's
  time zone, and gross profit for managers), a daily cash closing by payment method with the day's drawers and
  a low-stock reorder list. Sellers only see their own sales. Managers also get sales by product (with cost
  and gross profit, or the products in stock that didn't sell), sales by registered customer and a purchases
  summary (suppliers, top products, daily breakdown).
- Cost of goods sold: every purchase updates the product's weighted average cost, and every sale line keeps
  the cost it had when sold.

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

Login and password reset are rate limited per client address in memory (`app.rate-limit`), so run a
single instance; scaling out needs a shared store for the limits.

## Tests

```bash
./mvnw verify
```

## Environment variables

| Variable | Description |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` in containers (reads the variables below) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | PostgreSQL connection (`prod`; port defaults to 5432) |
| `CORS_ALLOWED_ORIGINS` | Comma-separated web client origins (default `http://localhost:5173`; none in `prod` unless set) |
| `PORT` | HTTP port (`prod`; defaults to 8080, Render sets it) |
| `JWT_SECRET` | Base64, at least 32 bytes (`openssl rand -base64 32`) |
| `JWT_MAX_SESSION_AGE` | How long after logging in a session ends, however often it refreshes (default `7d`; e.g. `12h`) |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | Initial admin, created if none exists |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP for password reset codes by email (optional; port defaults to 587 with STARTTLS) |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` / `TWILIO_FROM_NUMBER` | Twilio for password reset codes by SMS (optional; number in E.164) |
| `CLOUDINARY_CLOUD_NAME` / `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Cloudinary for product images (required for uploads in `prod`; elsewhere images go to `uploads/images`) |
| `IMAGES_BASE_URL` | Public URL of the local images folder (default `http://localhost:8080/images`; not used with Cloudinary) |
| `CASH_DRAWER_UNBALANCED_THRESHOLD` | Pesos of shortage or surplus from which a closed drawer counts as unbalanced in the cash closing (default `50.00`) |

Without SMTP or Twilio settings, reset codes for that channel aren't sent; outside `prod` they are written to the log so the flow can be tried locally.
Without Cloudinary settings, image uploads answer 503 in `prod` (Render's disk is wiped on every deploy).
