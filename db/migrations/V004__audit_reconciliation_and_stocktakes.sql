CREATE TABLE IF NOT EXISTS stocktake (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 sku_id BIGINT NOT NULL,
 sku_code VARCHAR(40) NOT NULL,
 sku_name VARCHAR(120) NOT NULL,
 actor_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 target_on_hand BIGINT NOT NULL,
 original_on_hand BIGINT NOT NULL,
 original_reserved BIGINT NOT NULL,
 inventory_version BIGINT NOT NULL,
 reason VARCHAR(240) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 confirmed_by BIGINT NULL,
 adjustment BIGINT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 confirmed_at DATETIME(6) NULL,
 UNIQUE KEY uk_stocktake_request(actor_id,request_key),
 KEY ix_stocktake_status(status,id),
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CHECK(target_on_hand BETWEEN 0 AND 1000000000),
 CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS operation_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 actor_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 entity_type VARCHAR(16) NOT NULL,
 entity_id BIGINT NOT NULL,
 entity_label VARCHAR(200) NOT NULL,
 before_json JSON NULL,
 after_json JSON NULL,
 request_id VARCHAR(36) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_audit_action_id(action,id)
) ENGINE=InnoDB;

SET @sf_has_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory' AND COLUMN_NAME='version');
SET @sf_ddl=IF(@sf_has_column=0,'ALTER TABLE inventory ADD COLUMN version BIGINT NOT NULL DEFAULT 0','SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl; EXECUTE sf_upgrade; DEALLOCATE PREPARE sf_upgrade;
SET @sf_has_column=(SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_ledger' AND COLUMN_NAME='stocktake_id');
SET @sf_ddl=IF(@sf_has_column=0,'ALTER TABLE inventory_ledger ADD COLUMN stocktake_id BIGINT NULL','SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl; EXECUTE sf_upgrade; DEALLOCATE PREPARE sf_upgrade;
SET @sf_has_fk=(SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='inventory_ledger' AND CONSTRAINT_NAME='fk_ledger_stocktake');
SET @sf_ddl=IF(@sf_has_fk=0,'ALTER TABLE inventory_ledger ADD CONSTRAINT fk_ledger_stocktake FOREIGN KEY(stocktake_id) REFERENCES stocktake(id)','SELECT 1');
PREPARE sf_upgrade FROM @sf_ddl; EXECUTE sf_upgrade; DEALLOCATE PREPARE sf_upgrade;
