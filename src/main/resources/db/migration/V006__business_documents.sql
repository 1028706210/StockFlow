CREATE TABLE receipt_document (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 actor_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 source VARCHAR(240) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 confirmed_at DATETIME(6) NULL,
 UNIQUE KEY uk_receipt_request(actor_id,request_key),
 KEY ix_receipt_status(status,id),
 CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED'))
) ENGINE=InnoDB;
CREATE TABLE receipt_item (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 receipt_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 sku_code VARCHAR(40) NOT NULL,
 sku_name VARCHAR(120) NOT NULL,
 quantity INT NOT NULL,
 UNIQUE KEY uk_receipt_sku(receipt_id,sku_id),
 FOREIGN KEY(receipt_id) REFERENCES receipt_document(id),
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CHECK(quantity BETWEEN 1 AND 1000000)
) ENGINE=InnoDB;
CREATE TABLE customer_return (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 order_id BIGINT NOT NULL,
 buyer_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 reason VARCHAR(240) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'REQUESTED',
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 confirmed_at DATETIME(6) NULL,
 UNIQUE KEY uk_return_request(buyer_id,request_key),
 KEY ix_return_order(order_id,status),
 KEY ix_return_buyer(buyer_id,id),
 KEY ix_return_status(status,id),
 FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CHECK(status IN ('REQUESTED','CONFIRMED','CANCELLED'))
) ENGINE=InnoDB;
CREATE TABLE return_item (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 return_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 sku_code VARCHAR(40) NULL,
 sku_name VARCHAR(120) NULL,
 quantity INT NOT NULL,
 unit_price DECIMAL(12,2) NOT NULL,
 UNIQUE KEY uk_return_sku(return_id,sku_id),
 FOREIGN KEY(return_id) REFERENCES customer_return(id),
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CHECK(quantity BETWEEN 1 AND 99),
 CHECK(unit_price>=0)
) ENGINE=InnoDB;
ALTER TABLE inventory_ledger
 ADD COLUMN receipt_id BIGINT NULL,
 ADD COLUMN return_id BIGINT NULL,
 ADD CONSTRAINT fk_ledger_receipt FOREIGN KEY(receipt_id) REFERENCES receipt_document(id),
 ADD CONSTRAINT fk_ledger_return FOREIGN KEY(return_id) REFERENCES customer_return(id);