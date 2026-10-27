-- Preserve NULL for historical orders: current catalog values are not historical truth.
ALTER TABLE order_item ADD COLUMN sku_code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD COLUMN sku_name VARCHAR(120) NULL;
