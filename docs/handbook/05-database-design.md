# StockFlow 数据库设计

## 数据范围与结构来源

当前有12张业务表，另有Flyway管理的flyway_schema_history。使用MySQL 8 InnoDB；本机私库默认utf8mb4_0900_as_cs，编码、账号、请求键等部分字段使用ASCII及区分大小写比较。Compose和CI库的默认collation由MySQL映像环境决定，不能假设所有环境字串比较完全相同。

本字典逐行取自执行中的迁移源：`src/main/resources/db/migration/B004__legacy_schema.sql`、V005、V006。SQL仍是DDL权威；下表的键、默认与CHECK按原文保留，所有关系不自动等同于数据库外键。

## 关系与用途

| 主体 | 关系与意义 |
|---|---|
| sku与inventory | 每商品最多一行库存，inventory.sku_id同时是PK与FK |
| customer_order与order_item | 一单多SKU，明细以order_id和sku_id复合主键去重 |
| receipt_document与receipt_item | 一张入库单多行，receipt_id及sku_id唯一 |
| customer_return与return_item | 一张退货多行，关联原订单并复制快照；return_id及sku_id唯一 |
| stocktake | 单商品盘点，保存建立时余额与version |
| inventory_ledger | 关联商品，可选order、stocktake、receipt、return来源 |
| app_user与业务执行者 | buyer_id、actor_id为身份逻辑关联，部分没有实体FK |
| operation_audit | entity_type及entity_id多型逻辑引用，保存before／after JSON和request_id |

业务表没有warehouse_id、tenant_id、库位、批次或财务分录，不能用作多仓或多租户结构。商品停用代替删除，现有API没有数据删除入口。

## 型别与约束策略

金额使用DECIMAL，数量用INT或BIGINT；API商品价格上限比DECIMAL(12,2)储存范围更小。名称／编码快照是历史显示数据，order_item的V005字段允许NULL，return_item保留这个未知性。密码仅储存BCrypt哈希。

唯一索引处理重复请求和明细，CHECK保护数量和部分状态；customer_order.status与ledger.operation_type目前没有完整枚举CHECK，依赖服务写入路径。数据库也没有跨表计算总额、订单有效退货数量或库存与流水自动相等的约束，这些由服务与对账处理。

## 字段字典

「定义」栏为DDL型别及限制原文，NULL／NOT NULL、默认和自增以此为准。下列字典包含V005、V006新增字段，未把Java响应计算字段当成储存字段。

### sku 商品

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| code | VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE | 商品编码 |
| name | VARCHAR(120) NOT NULL | 商品名称 |
| price | DECIMAL(12,2) NOT NULL | 目前商品单价 |
| active | BOOLEAN NOT NULL DEFAULT TRUE | 是否启用销售 |

sku 表级键与约束：

- `CHECK(price >= 0)`

### inventory 库存余额

| 字段 | 定义 | 说明 |
|---|---|---|
| sku_id | BIGINT PRIMARY KEY | 商品ID |
| on_hand | BIGINT NOT NULL DEFAULT 0 | 实存 |
| reserved | BIGINT NOT NULL DEFAULT 0 | 预留 |
| safety_stock | INT NOT NULL DEFAULT 10 | 安全线 |
| version | BIGINT NOT NULL DEFAULT 0 | 库存版本 |

inventory 表级键与约束：

- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `CHECK(safety_stock BETWEEN 1 AND 1000000)`
- `CHECK(on_hand >= reserved AND reserved >= 0 AND on_hand <= 1000000000)`

### customer_order 订单主表

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| buyer_id | BIGINT NOT NULL | 买家身份ID，逻辑关联 |
| request_key | VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL | 请求幂等键 |
| payload_hash | CHAR(64) CHARACTER SET ascii NOT NULL | 内容SHA-256 |
| status | VARCHAR(24) NOT NULL | 业务状态 |
| cancel_reason | VARCHAR(24) NULL | USER或EXPIRED |
| confirmed_at | DATETIME(6) NULL | 确认时间 |
| shipped_at | DATETIME(6) NULL | 发货时间 |
| completed_at | DATETIME(6) NULL | 收货完成时间 |
| cancelled_at | DATETIME(6) NULL | 取消时间 |
| total | DECIMAL(14,2) NOT NULL DEFAULT 0 | 订单总额 |
| expires_at | DATETIME(6) NOT NULL | 确认期限 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |

customer_order 表级键与约束：

- `UNIQUE KEY uk_buyer_request(buyer_id,request_key)`
- `KEY ix_expiration(status,expires_at)`

### order_item 订单明细

| 字段 | 定义 | 说明 |
|---|---|---|
| order_id | BIGINT NOT NULL | 原订单ID |
| sku_id | BIGINT NOT NULL | 商品ID |
| quantity | INT NOT NULL | 正整数数量 |
| unit_price | DECIMAL(12,2) NOT NULL | 原订单单价快照 |
| sku_code | VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL | 商品编码快照 |
| sku_name | VARCHAR(120) NULL | 商品名称快照 |

order_item 表级键与约束：

- `PRIMARY KEY(order_id,sku_id)`
- `FOREIGN KEY(order_id) REFERENCES customer_order(id)`
- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `CHECK(quantity BETWEEN 1 AND 99)`
- `CHECK(unit_price >= 0)`

### stocktake 盘点单

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| sku_id | BIGINT NOT NULL | 商品ID |
| sku_code | VARCHAR(40) NOT NULL | 商品编码快照 |
| sku_name | VARCHAR(120) NOT NULL | 商品名称快照 |
| actor_id | BIGINT NOT NULL | 操作人ID，逻辑关联 |
| request_key | VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL | 请求幂等键 |
| target_on_hand | BIGINT NOT NULL | 实盘目标 |
| original_on_hand | BIGINT NOT NULL | 建立时实存 |
| original_reserved | BIGINT NOT NULL | 建立时预留 |
| inventory_version | BIGINT NOT NULL | 盘点保存的版本 |
| reason | VARCHAR(240) NOT NULL | 盘点或退货原因 |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | 业务状态 |
| confirmed_by | BIGINT NULL | 盘点确认人ID |
| adjustment | BIGINT NULL | 有正负号的盘点差额 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |
| confirmed_at | DATETIME(6) NULL | 确认时间 |

stocktake 表级键与约束：

- `UNIQUE KEY uk_stocktake_request(actor_id,request_key)`
- `KEY ix_stocktake_status(status,id)`
- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `CHECK(target_on_hand BETWEEN 0 AND 1000000000)`
- `CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED'))`

### inventory_ledger 库存流水

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| sku_id | BIGINT NOT NULL | 商品ID |
| order_id | BIGINT NULL | 原订单ID |
| stocktake_id | BIGINT NULL | 可选盘点来源 |
| actor_id | BIGINT NOT NULL | 操作人ID，逻辑关联 |
| operation_type | VARCHAR(16) NOT NULL | 流水类型 |
| operation_key | VARCHAR(90) CHARACTER SET ascii COLLATE ascii_bin NOT NULL | 来源业务操作键 |
| quantity | INT NOT NULL | 正整数数量 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |
| receipt_id | BIGINT NULL | 入库单ID |
| return_id | BIGINT NULL | 退货单ID |

inventory_ledger 表级键与约束：

- `UNIQUE KEY uk_inventory_operation(operation_type,operation_key,sku_id)`
- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `FOREIGN KEY(order_id) REFERENCES customer_order(id)`
- `CONSTRAINT fk_ledger_stocktake FOREIGN KEY(stocktake_id) REFERENCES stocktake(id)`
- `CHECK(quantity > 0)`
- `CONSTRAINT fk_ledger_receipt FOREIGN KEY(receipt_id) REFERENCES receipt_document(id)`
- `CONSTRAINT fk_ledger_return FOREIGN KEY(return_id) REFERENCES customer_return(id)`

### app_user 账号

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| username | VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE | 登入名称 |
| password_hash | VARCHAR(100) NOT NULL | BCrypt哈希，非明文 |
| role | VARCHAR(16) NOT NULL | BUYER／WAREHOUSE／ADMIN |
| enabled | BOOLEAN NOT NULL DEFAULT TRUE | 账号可用状态 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |

app_user 表级键与约束：

- `CHECK(role IN ('BUYER','WAREHOUSE','ADMIN'))`

### operation_audit 操作审计

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| actor_id | BIGINT NOT NULL | 操作人ID，逻辑关联 |
| action | VARCHAR(32) NOT NULL | 审计动作 |
| entity_type | VARCHAR(16) NOT NULL | 实体类型 |
| entity_id | BIGINT NOT NULL | 实体ID，逻辑关联 |
| entity_label | VARCHAR(200) NOT NULL | 当次描述 |
| before_json | JSON NULL | 变更前JSON，可空 |
| after_json | JSON NULL | 变更后JSON，可空 |
| request_id | VARCHAR(36) NOT NULL | 请求追踪UUID |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |

operation_audit 表级键与约束：

- `KEY ix_audit_action_id(action,id)`

### receipt_document 入库主单

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| actor_id | BIGINT NOT NULL | 操作人ID，逻辑关联 |
| request_key | VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL | 请求幂等键 |
| payload_hash | CHAR(64) CHARACTER SET ascii NOT NULL | 内容SHA-256 |
| source | VARCHAR(240) NOT NULL | 入库来源或备注 |
| status | VARCHAR(16) NOT NULL DEFAULT 'DRAFT' | 业务状态 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |
| confirmed_at | DATETIME(6) NULL | 确认时间 |

receipt_document 表级键与约束：

- `UNIQUE KEY uk_receipt_request(actor_id,request_key)`
- `KEY ix_receipt_status(status,id)`
- `CHECK(status IN ('DRAFT','CONFIRMED','CANCELLED'))`

### receipt_item 入库明细

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| receipt_id | BIGINT NOT NULL | 入库单ID |
| sku_id | BIGINT NOT NULL | 商品ID |
| sku_code | VARCHAR(40) NOT NULL | 商品编码快照 |
| sku_name | VARCHAR(120) NOT NULL | 商品名称快照 |
| quantity | INT NOT NULL | 正整数数量 |

receipt_item 表级键与约束：

- `UNIQUE KEY uk_receipt_sku(receipt_id,sku_id)`
- `FOREIGN KEY(receipt_id) REFERENCES receipt_document(id)`
- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `CHECK(quantity BETWEEN 1 AND 1000000)`

### customer_return 退货主单

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| order_id | BIGINT NOT NULL | 原订单ID |
| buyer_id | BIGINT NOT NULL | 买家身份ID，逻辑关联 |
| request_key | VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL | 请求幂等键 |
| payload_hash | CHAR(64) CHARACTER SET ascii NOT NULL | 内容SHA-256 |
| reason | VARCHAR(240) NOT NULL | 盘点或退货原因 |
| status | VARCHAR(16) NOT NULL DEFAULT 'REQUESTED' | 业务状态 |
| created_at | DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 建立时间 |
| confirmed_at | DATETIME(6) NULL | 确认时间 |

customer_return 表级键与约束：

- `UNIQUE KEY uk_return_request(buyer_id,request_key)`
- `KEY ix_return_order(order_id,status)`
- `KEY ix_return_buyer(buyer_id,id)`
- `KEY ix_return_status(status,id)`
- `FOREIGN KEY(order_id) REFERENCES customer_order(id)`
- `CHECK(status IN ('REQUESTED','CONFIRMED','CANCELLED'))`

### return_item 退货明细

| 字段 | 定义 | 说明 |
|---|---|---|
| id | BIGINT PRIMARY KEY AUTO_INCREMENT | 自增主键 |
| return_id | BIGINT NOT NULL | 退货单ID |
| sku_id | BIGINT NOT NULL | 商品ID |
| sku_code | VARCHAR(40) NULL | 商品编码快照 |
| sku_name | VARCHAR(120) NULL | 商品名称快照 |
| quantity | INT NOT NULL | 正整数数量 |
| unit_price | DECIMAL(12,2) NOT NULL | 原订单单价快照 |

return_item 表级键与约束：

- `UNIQUE KEY uk_return_sku(return_id,sku_id)`
- `FOREIGN KEY(return_id) REFERENCES customer_return(id)`
- `FOREIGN KEY(sku_id) REFERENCES sku(id)`
- `CHECK(quantity BETWEEN 1 AND 99)`
- `CHECK(unit_price>=0)`


## 索引使用与取舍

下单按buyer_id、request_key查重；过期任务依status、expires_at配合ID游标；库存流水以自增ID向前游标查询，指定SKU时可用FK所需索引。盘点、入库、退货及审计各有状态或动作索引。未显式命名的外键支援索引可能由MySQL自动建立，不把生成名称当成跨版本契约。

目前使用LIKE包含匹配及OFFSET深页，并非所有查询都能靠普通B-tree避免扫描／排序；大量数据样本已看到订单filesort。不要为每种筛选盲目加索引，先用实际SQL、数据分布和EXPLAIN判断，并量化写入及维护成本。

## 迁移与数据保留

Flyway使用B004建立空库基线，V005新增订单名称／编码，V006新增入库退货表及流水关联。已有无历史表的已知V004结构，先校验再baseline4；未知或受损库不自动接管。根目录db/migrations保留旧审阅数据，schema.sql不再建表，不手工重放旧SQL。

迁移不为旧明细回填现名。schema_history记录版本和校验值，不能随意删改。后续变更新增V007等新档案并测空库、已知旧库、重启和数据保留；现有版本没有down migration或自动清理历史数据策略。
