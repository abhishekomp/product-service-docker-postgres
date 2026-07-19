INSERT INTO product(prod_num, p_name, sku_code) VALUES (1, 'Apple iPhone 16', 'APP2024') ON CONFLICT (prod_num) DO NOTHING;
INSERT INTO product(prod_num, p_name, sku_code) VALUES (2, 'Samsung S24', 'SAM2024') ON CONFLICT (prod_num) DO NOTHING;
ALTER SEQUENCE product_seq RESTART WITH 3;