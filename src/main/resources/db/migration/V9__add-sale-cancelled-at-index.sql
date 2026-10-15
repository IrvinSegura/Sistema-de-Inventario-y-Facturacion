-- Cash closing sums cancellations by the day they were made. Partial: cancelled sales are rare,
-- so the index stays small and completed sales (the bulk of the table) never touch it.
CREATE INDEX ix_sale_cancelled_at ON sale (cancelled_at) WHERE cancelled_at IS NOT NULL;
