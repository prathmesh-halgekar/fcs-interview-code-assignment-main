INSERT INTO store(id, name, quantityProductsInStock) VALUES (1, 'TONSTAD', 10);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (2, 'KALLAX', 5);
INSERT INTO store(id, name, quantityProductsInStock) VALUES (3, 'BESTÅ', 3);
ALTER SEQUENCE store_seq RESTART WITH 4;

INSERT INTO product(id, name, stock) VALUES (1, 'TONSTAD', 10);
INSERT INTO product(id, name, stock) VALUES (2, 'KALLAX', 5);
INSERT INTO product(id, name, stock) VALUES (3, 'BESTÅ', 3);
ALTER SEQUENCE product_seq RESTART WITH 4;

INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt) 
VALUES (1, 'MWH.001', 'ZWOLLE-001', 100, 10, '2024-07-01', null);
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt)
VALUES (2, 'MWH.012', 'AMSTERDAM-001', 50, 5, '2023-07-01', null);
INSERT INTO warehouse(id, businessUnitCode, location, capacity, stock, createdAt, archivedAt)
VALUES (3, 'MWH.023', 'TILBURG-001', 30, 27, '2021-02-01', null);
ALTER SEQUENCE warehouse_seq RESTART WITH 4;

INSERT INTO product_warehouse_store(id, product_id, warehouse_business_unit_code, store_id, created_at) VALUES (1, 1, 'MWH.001', 1, '2024-07-01');
INSERT INTO product_warehouse_store(id, product_id, warehouse_business_unit_code, store_id, created_at) VALUES (2, 2, 'MWH.012', 2, '2024-07-01');
INSERT INTO product_warehouse_store(id, product_id, warehouse_business_unit_code, store_id, created_at) VALUES (3, 3, 'MWH.023', 3, '2024-07-01');
ALTER SEQUENCE product_warehouse_store_seq RESTART WITH 4;
