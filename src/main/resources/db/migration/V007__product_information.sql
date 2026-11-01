-- Additive catalog information. Historical orders retain unknown values.
ALTER TABLE sku
  ADD COLUMN category VARCHAR(40) NOT NULL DEFAULT '',
  ADD COLUMN specification VARCHAR(120) NOT NULL DEFAULT '',
  ADD COLUMN unit VARCHAR(12) NOT NULL DEFAULT '件',
  ADD COLUMN description VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE order_item
  ADD COLUMN specification VARCHAR(120) NULL,
  ADD COLUMN unit VARCHAR(12) NULL;
