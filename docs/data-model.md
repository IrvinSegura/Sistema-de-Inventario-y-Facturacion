# Data Model

PostgreSQL 17. The schema changes only through Flyway migrations (`src/main/resources/db/migration`).

## ER Diagram

```mermaid
erDiagram
    app_user ||--o{ sale : records
    app_user |o--o{ sale : cancels
    app_user ||--o{ purchase : records
    app_user ||--o{ inventory_movement : performs
    app_user ||--o{ refresh_token : owns
    category ||--o{ product : groups
    supplier ||--o{ purchase : supplies
    purchase ||--|{ purchase_item : contains
    product ||--o{ purchase_item : "purchased in"
    customer ||--o{ sale : places
    sale ||--|{ sale_item : contains
    product ||--o{ sale_item : "sold in"
    product ||--o{ inventory_movement : "ledger of"
    purchase |o--o{ inventory_movement : originates
    sale |o--o{ inventory_movement : originates

    app_user {
        bigint id PK
        varchar name
        varchar email UK "case-insensitive"
        varchar password_hash "BCrypt"
        varchar role "ADMIN | SELLER"
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    category {
        bigint id PK
        varchar name UK "case-insensitive"
        varchar description
        numeric default_tax_rate "suggested VAT for new products"
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    product {
        bigint id PK
        varchar sku UK
        varchar barcode UK "8-14 digits, optional"
        varchar name
        varchar description
        varchar image_url "object storage URL, optional"
        bigint category_id FK
        numeric sale_price "pre-tax"
        numeric tax_rate "0.0000 or 0.1600"
        int stock "CHECK >= 0, atomic UPDATE only"
        int min_stock "low-stock alert"
        boolean active
        bigint version "optimistic lock"
        timestamptz created_at
        timestamptz updated_at
    }

    supplier {
        bigint id PK
        varchar name
        varchar tax_id UK "uppercase, optional"
        varchar email
        varchar phone
        varchar address
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    customer {
        bigint id PK
        varchar name
        varchar tax_id UK "uppercase, optional"
        varchar email
        varchar phone
        varchar address
        boolean active
        timestamptz created_at
        timestamptz updated_at
    }

    purchase {
        bigint id PK
        bigint supplier_id FK
        bigint user_id FK
        timestamptz purchase_date
        numeric total
        uuid idempotency_key UK "optional"
    }

    purchase_item {
        bigint id PK
        bigint purchase_id FK
        bigint product_id FK
        int quantity "> 0"
        numeric unit_cost
        numeric subtotal "= quantity * unit_cost"
    }

    sale {
        bigint id PK
        bigint sale_number UK "DB sequence"
        bigint customer_id FK
        bigint user_id FK
        timestamptz sale_date
        numeric subtotal
        numeric tax "sum of item taxes"
        numeric total "= subtotal + tax"
        varchar payment_method "CASH | CARD | TRANSFER"
        varchar status "COMPLETED | CANCELLED"
        timestamptz cancelled_at "set when cancelled"
        bigint cancelled_by FK "set when cancelled"
        varchar cancellation_reason "set when cancelled"
        uuid idempotency_key UK "optional"
    }

    sale_item {
        bigint id PK
        bigint sale_id FK
        bigint product_id FK
        int quantity "> 0"
        numeric unit_price "snapshot at sale time"
        numeric tax_rate "snapshot at sale time"
        numeric subtotal "= quantity * unit_price"
        numeric tax "= ROUND(subtotal * tax_rate, 2)"
    }

    refresh_token {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "SHA-256, raw token never stored"
        timestamptz expires_at
        timestamptz revoked_at "set on rotation or logout"
        varchar revocation_reason "ROTATED | LOGOUT | SESSIONS_REVOKED"
        timestamptz created_at
    }

    inventory_movement {
        bigint id PK
        bigint product_id FK
        varchar type "PURCHASE | SALE | SALE_CANCELLATION | ADJUSTMENT"
        int quantity "+ in / - out"
        int resulting_stock "from UPDATE RETURNING"
        bigint purchase_id FK "optional"
        bigint sale_id FK "optional"
        varchar reason "required for ADJUSTMENT"
        bigint user_id FK
        timestamptz movement_date "clock_timestamp()"
    }
```

## Design Decisions

| Decision | Why |
|---|---|
| Stock decremented with `UPDATE ... SET stock = stock - :qty WHERE id = :id AND stock >= :qty RETURNING stock`, backed by `CHECK (stock >= 0)` | Two sellers selling the last unit at once: only one succeeds. |
| `Product.stock` mapped `insertable = false, updatable = false` | Otherwise an admin editing the price could overwrite stock with a stale value and silently undo sales. |
| `version` on product | Optimistic locking for catalog edits; doesn't cover stock, so it never conflicts with sales. |
| Lock products in `product_id` order; `lock_timeout = 5s` | Prevents deadlocks between multi-product sales and endless waits on hot rows. |
| `idempotency_key` on sale and purchase | A double click or retry returns the existing document instead of creating a duplicate. |
| Atomic cancellation (`UPDATE sale SET status = 'CANCELLED', cancelled_at, cancelled_by, cancellation_reason WHERE id = :id AND status = 'COMPLETED'`) + unique index per (sale, product, type) | Concurrent cancellations can't return stock twice; who, when and why is always recorded (enforced by `ck_sale_status`). |
| `barcode` (optional, unique, 8-14 digits) separate from `sku` | Grocery items are sold by scanning; `sku` stays as the internal code, also for unlabeled items. |
| `image_url` only; images live in object storage (Cloudinary/S3) | Binary files bloat and slow down the DB. |
| `payment_method` on sale | Needed for reports and cash closing. |
| `resulting_stock` from `RETURNING`; `movement_date` uses `clock_timestamp()` | The ledger stays consistent and correctly ordered under concurrency. |
| `sale_number` from a DB sequence, mapped with `@Generated(event = INSERT)` | `MAX()+1` duplicates numbers; `@Generated` reads the value back. |
| `unit_price` and `tax_rate` copied into `sale_item` | Past sales keep their original price and tax. |
| Seeded grocery categories with `default_tax_rate` (Pantry, Dairy, Bakery 0%; Beverages, Snacks, Cleaning, Personal Care 16%) | Pre-fills the product VAT so classification errors are less likely; each product can still override it. |
| Refresh tokens stored as SHA-256 hashes, rotated on every use; reuse of a `ROTATED` token revokes all of the user's sessions; expired ones deleted daily | Stolen refresh tokens are detected without logging users out for a stale logged-out token; a DB leak doesn't expose usable tokens. |
| Emails stored lowercase (`CHECK (email = LOWER(email))`) with a plain unique index | Case-insensitive uniqueness and indexed login lookups. |
| VAT rate per product (`tax_rate`: 0% basic food, 16% general); tax computed and rounded per line (HALF_UP); `sale.tax` = sum of line taxes | Mexican grocery store: sugar or rice pay 0%, soda or snacks 16%. |
| Money as `NUMERIC(12,2)` / `BigDecimal`; `sale_price` is pre-tax | No floating-point rounding errors; totals computed server-side. |
| Sales are cancelled, catalogs soft-deleted (`active`) | History is never broken. |
| Seeded "PUBLICO EN GENERAL" customer (`XAXX010101000`), looked up by tax ID constant | Every sale has a customer, as Mexican invoicing requires. |
| First ADMIN created at startup from `ADMIN_EMAIL` / `ADMIN_PASSWORD` | A password hash in a migration would be a versioned credential. |
| `BIGINT IDENTITY` ids, `TIMESTAMPTZ` dates | PostgreSQL standard; time zone-safe reports. |

## Indexes

Details live in `V1__initial-schema.sql`. Criteria:

- Every queried FK is indexed. The `UNIQUE (document_id, product_id)` constraints on line items also cover the document FK.
- Date indexes for reports: `sale(sale_date)`, `purchase(purchase_date)`, `sale(user_id, sale_date)` and `inventory_movement(product_id, movement_date)`.
- `stock` is not indexed: it's the hottest column and the low-stock report scans a small catalog.

## Role Permissions

| Action | ADMIN | SELLER |
|---|:---:|:---:|
| Manage users | ✅ | ❌ |
| Manage products, categories, suppliers | ✅ | ❌ |
| View products and stock | ✅ | ✅ |
| Record purchases | ✅ | ❌ |
| Manage customers | ✅ | ✅ |
| Record sales | ✅ | ✅ |
| Cancel sales | ✅ | ❌ |
| Manual stock adjustments | ✅ | ❌ |
| Reports | ✅ | Own sales only |
