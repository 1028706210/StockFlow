CREATE TABLE IF NOT EXISTS app_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(16) NOT NULL,
 enabled BOOLEAN NOT NULL DEFAULT TRUE,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CHECK(role IN ('BUYER','WAREHOUSE','ADMIN'))
) ENGINE=InnoDB AUTO_INCREMENT=100;

-- Idempotent upgrade for existing P0 databases; DATABASE() must be a StockFlow schema.
SET @sf_has_reason = (SELECT COUNT(*) FROM information_schema.COLUMNS
 WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='customer_order' AND COLUMN_NAME='cancel_reason');
SET @sf_ddl = IF(@sf_has_reason=0,
 'ALTER TABLE customer_order ADD COLUMN cancel_reason VARCHAR(24) NULL AFTER status',
 'SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl;
EXECUTE sf_upgrade;
DEALLOCATE PREPARE sf_upgrade;
