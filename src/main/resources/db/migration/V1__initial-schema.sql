-- Initial schema. Design rationale: docs/data-model.md

-- =====================================================================
-- Users ("user" is reserved in PostgreSQL)
-- =====================================================================
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_email UNIQUE (email),
    -- Stored lowercase: uniqueness is case-insensitive and lookups by email use the index.
    CONSTRAINT ck_app_user_email_lower CHECK (email = LOWER(email)),
    CONSTRAINT ck_app_user_role CHECK (role IN ('ADMIN', 'SELLER'))
);

-- =====================================================================
-- Catalogs
-- =====================================================================
CREATE TABLE category (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    description      VARCHAR(255),
    -- Suggested VAT for new products in this category; each product keeps its own rate.
    default_tax_rate NUMERIC(5,4) NOT NULL,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_category_default_tax_rate CHECK (default_tax_rate >= 0 AND default_tax_rate < 1)
);

CREATE UNIQUE INDEX uk_category_name ON category (LOWER(name));

CREATE TABLE product (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku         VARCHAR(50)   NOT NULL,
    -- EAN-8, UPC-A, EAN-13 or GTIN-14; optional for unlabeled items.
    barcode     VARCHAR(14),
    name        VARCHAR(150)  NOT NULL,
    description VARCHAR(500),
    -- URL in object storage (Cloudinary/S3); images are never stored in the DB.
    image_url   VARCHAR(500),
    category_id BIGINT        NOT NULL,
    sale_price  NUMERIC(12,2) NOT NULL,
    -- VAT rate as a fraction: 0.0000 (basic food) or 0.1600 (general).
    tax_rate    NUMERIC(5,4)  NOT NULL,
    -- Changed only by atomic UPDATEs; never written by the JPA entity.
    stock       INTEGER       NOT NULL DEFAULT 0,
    min_stock   INTEGER       NOT NULL DEFAULT 0,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT uk_product_barcode UNIQUE (barcode),
    CONSTRAINT ck_product_barcode CHECK (barcode ~ '^[0-9]{8,14}$'),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES category (id),
    CONSTRAINT ck_product_sale_price CHECK (sale_price >= 0),
    CONSTRAINT ck_product_tax_rate CHECK (tax_rate >= 0 AND tax_rate < 1),
    -- Last line of defense against overselling.
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_min_stock CHECK (min_stock >= 0)
);

CREATE INDEX ix_product_category ON product (category_id);

CREATE TABLE supplier (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    tax_id     VARCHAR(13),
    email      VARCHAR(150),
    phone      VARCHAR(20),
    address    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_supplier_tax_id UNIQUE (tax_id),
    -- Uppercase so uniqueness doesn't depend on input format.
    CONSTRAINT ck_supplier_tax_id_upper CHECK (tax_id = UPPER(tax_id))
);

CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    tax_id     VARCHAR(13),
    email      VARCHAR(150),
    phone      VARCHAR(20),
    address    VARCHAR(255),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_customer_tax_id UNIQUE (tax_id),
    CONSTRAINT ck_customer_tax_id_upper CHECK (tax_id = UPPER(tax_id))
);

-- =====================================================================
-- Purchases (stock in)
-- =====================================================================
CREATE TABLE purchase (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supplier_id     BIGINT        NOT NULL,
    user_id         BIGINT        NOT NULL,
    purchase_date   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    total           NUMERIC(12,2) NOT NULL,
    -- Optional Idempotency-Key header: retries don't duplicate the purchase.
    idempotency_key UUID,
    CONSTRAINT uk_purchase_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_purchase_supplier FOREIGN KEY (supplier_id) REFERENCES supplier (id),
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_purchase_total CHECK (total >= 0)
);

CREATE INDEX ix_purchase_supplier ON purchase (supplier_id);
CREATE INDEX ix_purchase_user ON purchase (user_id);
CREATE INDEX ix_purchase_date ON purchase (purchase_date);

CREATE TABLE purchase_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    purchase_id BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    quantity    INTEGER       NOT NULL,
    unit_cost   NUMERIC(12,2) NOT NULL,
    subtotal    NUMERIC(12,2) NOT NULL,
    -- Also serves as the purchase_id index (leading column).
    CONSTRAINT uk_purchase_item_product UNIQUE (purchase_id, product_id),
    CONSTRAINT fk_purchase_item_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id),
    CONSTRAINT fk_purchase_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_purchase_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_item_unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT ck_purchase_item_subtotal CHECK (subtotal = quantity * unit_cost)
);

CREATE INDEX ix_purchase_item_product ON purchase_item (product_id);

-- =====================================================================
-- Sales (stock out)
-- =====================================================================
-- Concurrency-safe sale number (never MAX()+1). Gaps on rollback are acceptable.
CREATE SEQUENCE sale_number_seq START WITH 1;

CREATE TABLE sale (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_number         BIGINT        NOT NULL DEFAULT nextval('sale_number_seq'),
    customer_id         BIGINT        NOT NULL,
    user_id             BIGINT        NOT NULL,
    sale_date           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    subtotal            NUMERIC(12,2) NOT NULL,
    tax                 NUMERIC(12,2) NOT NULL,
    total               NUMERIC(12,2) NOT NULL,
    payment_method      VARCHAR(20)   NOT NULL,
    status              VARCHAR(20)   NOT NULL DEFAULT 'COMPLETED',
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        BIGINT,
    cancellation_reason VARCHAR(255),
    -- Optional Idempotency-Key header: double clicks or retries don't duplicate the sale.
    idempotency_key     UUID,
    CONSTRAINT uk_sale_number UNIQUE (sale_number),
    CONSTRAINT uk_sale_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_sale_customer FOREIGN KEY (customer_id) REFERENCES customer (id),
    CONSTRAINT fk_sale_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_sale_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES app_user (id),
    CONSTRAINT ck_sale_amounts CHECK (subtotal >= 0 AND tax >= 0 AND total = subtotal + tax),
    CONSTRAINT ck_sale_payment_method CHECK (payment_method IN ('CASH', 'CARD', 'TRANSFER')),
    -- Cancellation data is present if and only if the sale is cancelled.
    CONSTRAINT ck_sale_status CHECK (
        (status = 'COMPLETED' AND cancelled_at IS NULL AND cancelled_by IS NULL AND cancellation_reason IS NULL) OR
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by IS NOT NULL
                              AND cancellation_reason IS NOT NULL AND btrim(cancellation_reason) <> '')
    )
);

ALTER SEQUENCE sale_number_seq OWNED BY sale.sale_number;

CREATE INDEX ix_sale_date ON sale (sale_date);
CREATE INDEX ix_sale_customer ON sale (customer_id);
-- Covers the FK and the "sales per seller per period" report.
CREATE INDEX ix_sale_user_date ON sale (user_id, sale_date);

CREATE TABLE sale_item (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sale_id    BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    quantity   INTEGER       NOT NULL,
    -- Price and tax rate snapshots at sale time.
    unit_price NUMERIC(12,2) NOT NULL,
    tax_rate   NUMERIC(5,4)  NOT NULL,
    subtotal   NUMERIC(12,2) NOT NULL,
    tax        NUMERIC(12,2) NOT NULL,
    -- Also serves as the sale_id index (leading column).
    CONSTRAINT uk_sale_item_product UNIQUE (sale_id, product_id),
    CONSTRAINT fk_sale_item_sale FOREIGN KEY (sale_id) REFERENCES sale (id),
    CONSTRAINT fk_sale_item_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT ck_sale_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_sale_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT ck_sale_item_subtotal CHECK (subtotal = quantity * unit_price),
    CONSTRAINT ck_sale_item_tax_rate CHECK (tax_rate >= 0 AND tax_rate < 1),
    -- Per-line rounding, HALF_UP (matches BigDecimal RoundingMode.HALF_UP).
    CONSTRAINT ck_sale_item_tax CHECK (tax = ROUND(subtotal * tax_rate, 2))
);

CREATE INDEX ix_sale_item_product ON sale_item (product_id);

-- =====================================================================
-- Refresh tokens (rotated on every use)
-- =====================================================================
CREATE TABLE refresh_token (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT      NOT NULL,
    -- SHA-256 hex of the token; the raw token is never stored.
    token_hash        VARCHAR(64) NOT NULL,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    -- Only reuse of a ROTATED token signals theft; LOGOUT/SESSIONS_REVOKED don't.
    revocation_reason VARCHAR(20),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_refresh_token_revocation CHECK (
        (revoked_at IS NULL AND revocation_reason IS NULL) OR
        (revoked_at IS NOT NULL AND revocation_reason IN ('ROTATED', 'LOGOUT', 'SESSIONS_REVOKED'))
    )
);

-- Only live tokens are bulk-revoked; history stays out of the index.
CREATE INDEX ix_refresh_token_user_active ON refresh_token (user_id) WHERE revoked_at IS NULL;
-- Supports the expired-token cleanup job.
CREATE INDEX ix_refresh_token_expires_at ON refresh_token (expires_at);

-- =====================================================================
-- Stock ledger: one row per stock change
-- =====================================================================
CREATE TABLE inventory_movement (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id      BIGINT       NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    -- Positive = in, negative = out.
    quantity        INTEGER      NOT NULL,
    -- Taken from UPDATE ... RETURNING stock, never from a prior read.
    resulting_stock INTEGER      NOT NULL,
    purchase_id     BIGINT,
    sale_id         BIGINT,
    reason          VARCHAR(255),
    user_id         BIGINT       NOT NULL,
    -- clock_timestamp() = actual insert time (now() = transaction start), so order matches reality.
    movement_date   TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT fk_inventory_movement_product FOREIGN KEY (product_id) REFERENCES product (id),
    CONSTRAINT fk_inventory_movement_purchase FOREIGN KEY (purchase_id) REFERENCES purchase (id),
    CONSTRAINT fk_inventory_movement_sale FOREIGN KEY (sale_id) REFERENCES sale (id),
    CONSTRAINT fk_inventory_movement_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT ck_inventory_movement_resulting_stock CHECK (resulting_stock >= 0),
    -- Each type has a fixed sign and source document.
    CONSTRAINT ck_inventory_movement_type CHECK (
        (type = 'PURCHASE'          AND quantity > 0  AND purchase_id IS NOT NULL AND sale_id IS NULL) OR
        (type = 'SALE'              AND quantity < 0  AND sale_id IS NOT NULL     AND purchase_id IS NULL) OR
        (type = 'SALE_CANCELLATION' AND quantity > 0  AND sale_id IS NOT NULL     AND purchase_id IS NULL) OR
        (type = 'ADJUSTMENT'        AND quantity <> 0 AND purchase_id IS NULL     AND sale_id IS NULL
                                    AND reason IS NOT NULL AND btrim(reason) <> '')
    )
);

-- Product ledger; order by (movement_date, id).
CREATE INDEX ix_inventory_movement_product_date ON inventory_movement (product_id, movement_date);

-- One movement per document line and type (e.g. a sale can't be cancelled twice).
-- Partial, so they also index the purchase_id / sale_id FKs without NULL rows.
CREATE UNIQUE INDEX uk_inventory_movement_purchase_product
    ON inventory_movement (purchase_id, product_id) WHERE purchase_id IS NOT NULL;
CREATE UNIQUE INDEX uk_inventory_movement_sale_product_type
    ON inventory_movement (sale_id, product_id, type) WHERE sale_id IS NOT NULL;

-- No user_id index: users are soft-deleted and this is the most write-heavy table.
