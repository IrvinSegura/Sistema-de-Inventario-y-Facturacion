-- Generic walk-in customer; name and tax ID are mandated by Mexico's tax authority (SAT).
INSERT INTO customer (name, tax_id)
VALUES ('PUBLICO EN GENERAL', 'XAXX010101000');

-- Default grocery categories with their usual VAT rate in Mexico.
INSERT INTO category (name, description, default_tax_rate) VALUES
    ('Pantry',        'Rice, beans, sugar, oil, pasta, canned food', 0.0000),
    ('Dairy',         'Milk, cheese, yogurt, eggs',                  0.0000),
    ('Bakery',        'Bread, tortillas',                            0.0000),
    ('Beverages',     'Soda, juice, water',                          0.1600),
    ('Snacks',        'Chips, candy, cookies',                       0.1600),
    ('Cleaning',      'Detergent, bleach, cleaning supplies',        0.1600),
    ('Personal Care', 'Soap, shampoo, toothpaste, toilet paper',     0.1600);
