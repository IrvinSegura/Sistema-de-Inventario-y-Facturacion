-- Starting cost of products already purchased: every purchase replayed in ledger order with the same
-- rule as recordPurchase (the stock before each one is resulting_stock - quantity), restarting the
-- average whenever a purchase found no units on hand. Only row locks on the purchased products.
DO $$
DECLARE
    movement RECORD;
    current_product BIGINT;
    cost NUMERIC(14,4);
BEGIN
    FOR movement IN
        SELECT im.product_id, im.quantity, im.resulting_stock - im.quantity AS stock_before, pi.unit_cost
        FROM inventory_movement im
        JOIN purchase_item pi ON pi.purchase_id = im.purchase_id AND pi.product_id = im.product_id
        WHERE im.type = 'PURCHASE'
        ORDER BY im.product_id, im.id
    LOOP
        IF movement.product_id IS DISTINCT FROM current_product THEN
            IF current_product IS NOT NULL THEN
                UPDATE product SET average_cost = cost WHERE id = current_product;
            END IF;
            current_product := movement.product_id;
            cost := NULL;
        END IF;
        IF cost IS NULL OR movement.stock_before = 0 THEN
            cost := movement.unit_cost;
        ELSE
            cost := ROUND((movement.stock_before * cost + movement.quantity * movement.unit_cost)
                / (movement.stock_before + movement.quantity), 4);
        END IF;
    END LOOP;
    IF current_product IS NOT NULL THEN
        UPDATE product SET average_cost = cost WHERE id = current_product;
    END IF;
END $$;

-- VALIDATE takes SHARE UPDATE EXCLUSIVE: it scans the rows while sales and purchases keep writing.
ALTER TABLE product VALIDATE CONSTRAINT ck_product_average_cost;
ALTER TABLE sale_item VALIDATE CONSTRAINT ck_sale_item_unit_cost;
