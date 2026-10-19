-- CFDI 4.0 receiver data besides the tax ID and name: the postal code of the fiscal address and the
-- SAT tax regime (c_RegimenFiscal code; the known codes live in TaxRegime).
ALTER TABLE customer
    ADD COLUMN fiscal_postal_code VARCHAR(5),
    ADD COLUMN tax_regime         VARCHAR(3),
    ADD CONSTRAINT ck_customer_fiscal_postal_code CHECK (fiscal_postal_code ~ '^[0-9]{5}$'),
    ADD CONSTRAINT ck_customer_tax_regime CHECK (tax_regime ~ '^[0-9]{3}$'),
    -- The API takes tax ID, postal code and regime together; customers with a tax ID saved before
    -- this migration (and the walk-in one) have neither of the other two until they are edited.
    ADD CONSTRAINT ck_customer_fiscal_data CHECK (
        (fiscal_postal_code IS NULL) = (tax_regime IS NULL)
        AND (tax_regime IS NULL OR tax_id IS NOT NULL));
