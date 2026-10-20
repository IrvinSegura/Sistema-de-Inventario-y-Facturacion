-- Cost of goods sold, for gross profit. Schema only: nullable columns without a default are a catalog
-- change, so the ACCESS EXCLUSIVE locks they need last milliseconds, and NOT VALID skips scanning
-- the existing rows (V17 fills the costs and validates the checks without blocking sales).

-- Like stock, product.average_cost is written only by the purchase statement in
-- InventoryMovementRepository: a weighted average of the units on hand and the ones received, with
-- four decimals so repeated averaging doesn't drift. NULL until first purchased.
ALTER TABLE product
    ADD COLUMN average_cost NUMERIC(14,4),
    ADD CONSTRAINT ck_product_average_cost CHECK (average_cost >= 0) NOT VALID;

-- Snapshot of the product's average cost when the line was sold, stamped by the same statement that
-- takes the stock (under its row lock). NULL for sales recorded before this migration and for
-- products never purchased: reports treat their cost as unknown instead of zero.
ALTER TABLE sale_item
    ADD COLUMN unit_cost NUMERIC(14,4),
    ADD CONSTRAINT ck_sale_item_unit_cost CHECK (unit_cost >= 0) NOT VALID;
