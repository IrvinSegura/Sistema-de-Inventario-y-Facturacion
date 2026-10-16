-- Product names are unique ignoring case, like category names: two "Coca-Cola 600 ml" in the catalog
-- would be indistinguishable at the checkout. Also serves the service's duplicate check.
CREATE UNIQUE INDEX uk_product_name ON product (LOWER(name));
