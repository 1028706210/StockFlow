CREATE TABLE IF NOT EXISTS sku (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 code VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 name VARCHAR(120) NOT NULL,
 price DECIMAL(12,2) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 CHECK(price >= 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory (
 sku_id BIGINT PRIMARY KEY,
 on_hand BIGINT NOT NULL DEFAULT 0,
 reserved BIGINT NOT NULL DEFAULT 0,
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CHECK(on_hand >= reserved AND reserved >= 0 AND on_hand <= 1000000000)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS customer_order (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 buyer_id BIGINT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 payload_hash CHAR(64) CHARACTER SET ascii NOT NULL,
 status VARCHAR(24) NOT NULL,
 total DECIMAL(14,2) NOT NULL DEFAULT 0,
 expires_at DATETIME(6) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_buyer_request(buyer_id,request_key),
 KEY ix_expiration(status,expires_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS order_item (
 order_id BIGINT NOT NULL,
 sku_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 unit_price DECIMAL(12,2) NOT NULL,
 PRIMARY KEY(order_id,sku_id),
 FOREIGN KEY(order_id) REFERENCES customer_order(id),
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 CHECK(quantity BETWEEN 1 AND 99),
 CHECK(unit_price >= 0)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS inventory_ledger (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 sku_id BIGINT NOT NULL,
 order_id BIGINT NULL,
 actor_id BIGINT NOT NULL,
 operation_type VARCHAR(16) NOT NULL,
 operation_key VARCHAR(90) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 quantity INT NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uk_inventory_operation(operation_type,operation_key,sku_id),
 FOREIGN KEY(sku_id) REFERENCES sku(id),
 FOREIGN KEY(order_id) REFERENCES customer_order(id),
 CHECK(quantity > 0)
) ENGINE=InnoDB;
