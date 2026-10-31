-- Additive, repeatable upgrade. Existing lifecycle times remain NULL; never infer historical events.
SET @sf_has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory' AND COLUMN_NAME='safety_stock');
SET @sf_ddl = IF(@sf_has_column=0, 'ALTER TABLE inventory ADD COLUMN safety_stock INT NOT NULL DEFAULT 10 CHECK(safety_stock BETWEEN 1 AND 1000000)', 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;

SET @sf_has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_order' AND COLUMN_NAME='confirmed_at');
SET @sf_ddl = IF(@sf_has_column=0, 'ALTER TABLE customer_order ADD COLUMN confirmed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;

SET @sf_has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_order' AND COLUMN_NAME='shipped_at');
SET @sf_ddl = IF(@sf_has_column=0, 'ALTER TABLE customer_order ADD COLUMN shipped_at DATETIME(6) NULL', 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;

SET @sf_has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_order' AND COLUMN_NAME='completed_at');
SET @sf_ddl = IF(@sf_has_column=0, 'ALTER TABLE customer_order ADD COLUMN completed_at DATETIME(6) NULL', 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;

SET @sf_has_column = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_order' AND COLUMN_NAME='cancelled_at');
SET @sf_ddl = IF(@sf_has_column=0, 'ALTER TABLE customer_order ADD COLUMN cancelled_at DATETIME(6) NULL', 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;
