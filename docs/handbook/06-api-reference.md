# StockFlow API 参考

## 通用协议

本机基址为 `http://127.0.0.1:8082`；Compose默认为8083。成功响应直接返回资源或分页物件，没有统一的code／data外壳。JSON字段使用camelCase，枚举值使用大写；正整数ID直接传数字，不要把画面上的SF-、RC-、RT-前缀放入路径参数。

JSON写入使用 `Content-Type: application/json`，登入例外，使用 `application/x-www-form-urlencoded` 的username与password。未知JSON字段和整数字段中的小数会被拒绝。金额是JSON数值，后端BigDecimal处理；时间是无时区的ISO本地时间字串，不可假设自带Z或偏移。

## 浏览器会话流程

1. GET `/api/auth/csrf`，保存Cookie并取得 `{token,headerName}`。
2. POST `/api/auth/register` 或表单登入 `/api/auth/login`，使用上一步回传的headerName与token。注册返回201但不建立登入会话。
3. 登入成功后重新取得CSRF，再访问 `/api/auth/me`；浏览器保持同源Cookie，后续写入均带当前CSRF。
4. POST `/api/auth/logout` 带CSRF，返回204并失效会话；本地页面清除身份相关状态。

登入成功示例为 `{"id":100,"username":"sample_buyer","roles":["ROLE_BUYER"]}`。管理员同时具有ROLE_ADMIN和ROLE_WAREHOUSE。自动化客户端可使用HTTP Basic；此模式请使用变量注入本机口令，避免写入脚本或提交。Cookie会话和Basic的安全边界见系统设计。

## 分页与查询

| 参数 | 规则 | 适用范围 |
|---|---|---|
| q | 最多120字符，服务端按SQL定义查询并转义LIKE万用字符 | 各主列表；不是全站全文检索 |
| status | 精确枚举，默认all | 商品、库存、订单、盘点、入库及退货 |
| page／size | page为1至1000000；size为1至100，默认1／8 | 一般分页 |
| beforeId／limit | beforeId为正整数；limit为1至100，默认20 | 库存流水和预警游标 |
| from／to | YYYY-MM-DD，年份1000至9998，首尾日包含，倒置400 | 订单／履约、流水、盘点、审计；按建立时间 |
| minTotal／maxTotal | 非负、最多两位小数，上限999999999999.99，倒置400 | 订单／履约列表及导出 |
| type／skuId | 流水类型或正整数商品ID | 库存流水 |
| threshold | 1至1000000，省略使用每商品安全线 | 预警，临时覆盖不修改保存规则 |
| mismatchOnly | true或false，默认true | 对账 |
| action | 审计动作枚举，空表示全部 | 操作审计 |

一般分页为 `{items:[],total:0,page:1,size:8,totalPages:1}`，无数据时totalPages至少1，超出最后一页会收敛页码。流水为 `{items:[],nextCursor:null}`；预警另外有total和threshold，默认商品规则模式的顶层threshold为null。游标为null表示没有下一页，不自行猜测下一个ID。

q在商品、库存、流水、预警主要匹配编码／名称；订单匹配ID及SF-ID，仓管另匹配buyerId；盘点匹配商品快照和单号；审计匹配实体标签和ID；入库匹配来源／数字单号，退货匹配原因／数字单号／原订单数字ID。因此入库页显示RC-123时，API搜寻可用q=123，不保证q=RC-123能匹配。

## 主要写入契约

### 商品

POST `/api/skus`：`{"code":"DOC-DEMO-001","name":"演示商品","price":19.90}`。code为1至40位大写字母、数字、下划线或连字号；name非空且最多120字符；price为0至999999.99、最多两位小数。返回201及 `{id,code,name,price,active}`，同事务建立零库存。PUT `/api/skus/{id}`只接收name、price、active，active为必填布林。

### 直接收货与库存政策

POST `/api/inventory/{skuId}/receipts`：`{"operationKey":"receive-demo-001","quantity":20}`，返回200库存物件 `{skuId,code,onHand,reserved,available,safetyStock}`。同键不同数量409。PUT `/api/inventory/{skuId}/policy`：`{"safetyStock":10}`，1至1000000。直接收货是相容快捷入口，没有正式来源单据；需单据追踪请用入库单。

### 订单

POST `/api/orders`：`{"requestKey":"order-demo-001","items":[{"skuId":1,"quantity":2}]}`，首建201，重放200。最多10种不同SKU，重复SKU合并后数量1至99；原始阵列1至990行。不能传buyerId、unitPrice、total或status。

详情物件含id、status、total、expiresAt、buyerId、cancelReason、createdAt、confirmedAt、shippedAt、completedAt、cancelledAt及items；items含skuId、quantity、unitPrice、skuCode、skuName。列表Summary只含id、status、total、expiresAt、buyerId、cancelReason。confirm、cancel、complete、ship都是无请求体POST，返回200及目前详情；不合法状态409。

### 盘点

POST `/api/stocktakes`：`{"requestKey":"count-demo-001","skuId":1,"targetOnHand":18,"reason":"例行实盘"}`。targetOnHand为0至1000000000，reason非空且最多240字符；首建201、重放200。响应保留skuCode、skuName、originalOnHand、originalReserved、inventoryVersion、targetOnHand、status、actorId、confirmedBy、adjustment、createdAt、confirmedAt等。confirm与cancel为无体POST。STOCK_CHANGED需重新实盘并建立新草稿，不能盲目重试旧版本。

### 入库单

POST `/api/receipts`：`{"requestKey":"receipt-demo-001","source":"演示收货","items":[{"skuId":1,"quantity":20},{"skuId":2,"quantity":5}]}`。source非空且最多240字符；1至10行唯一SKU，每行1至1000000。首建201、重放200，响应含id、source、status、actorId、createdAt、confirmedAt、items；明细含id、skuId、skuCode、skuName、quantity。confirm与cancel返回200；不存在部分确认、编辑或删除接口。

### 退货

POST `/api/returns`：`{"requestKey":"return-demo-001","orderId":1,"reason":"整单实物退回"}`。orderId为本人已完成订单，reason非空且最多240字符。首建201、重放200；items由服务端复制，不能指定部分SKU或数量。响应含id、orderId、buyerId、reason、status、createdAt、confirmedAt及items，明细另含原unitPrice。仓管confirm、本人才可cancel，均无体POST。RETURN_EXISTS表示原订单已有有效或确认退货。

## 导出与响应边界

三个导出接口回传 `text/csv;charset=UTF-8`，含UTF-8 BOM及attachment档名，Cache-Control为no-store。按照完整筛选集合导出而非单页，最多10000行，超过返回422 EXPORT_LIMIT，应缩小条件。

订单CSV字段是订单号、状态、金额、确认截止时间、买家编号、取消原因；没有商品明细。库存CSV是商品编码、商品名称、实存、预留、可用、安全库存。文字采双引号转义，危险公式前缀加单引号。没有CSV汇入接口。

## 错误契约

错误例：`{"code":"INSUFFICIENT_STOCK","message":"Insufficient available stock","requestId":"伺服器产生的UUID"}`。每次请求另回X-Request-Id；前端可翻译message，不以英文message作稳定判断。

| HTTP | code示例 | 客户端处理 |
|---|---|---|
| 400 | INVALID_REQUEST | 修正字段、日期、枚举或数量 |
| 401 | UNAUTHENTICATED | 重新登入，清除旧身份数据 |
| 403 | FORBIDDEN | 检查角色及CSRF，不自动降级权限 |
| 404 | NOT_FOUND | 资源不存在、停用不可下单或非本人资源 |
| 409 | USERNAME_TAKEN、SKU_CODE_EXISTS | 使用其他合法唯一值 |
| 409 | IDEMPOTENCY_CONFLICT | 核对原请求，不能更换内容强行重放 |
| 409 | INSUFFICIENT_STOCK、INVENTORY_LIMIT | 重新查库存或更正数量 |
| 409 | INVALID_ORDER_STATE、ORDER_EXPIRED、RETURN_EXISTS | 刷新资源状态后决定下一步 |
| 409 | STOCK_CHANGED、RESERVED_STOCK、INVENTORY_CONFLICT | 重新实盘或核对数据，不盲目覆盖 |
| 405／415 | METHOD_NOT_ALLOWED／UNSUPPORTED_MEDIA_TYPE | 修正HTTP方法或Content-Type |
| 422 | EXPORT_LIMIT | 缩小导出范围 |
| 500 | INTERNAL_ERROR | 保留requestId排查，未知结果重试原键 |
| 503 | RETRY_LATER；就绪接口为status DOWN | 暂停或稍后重试，保留原键 |

下表为现有接口清单。B=买家本人；W=仓管或管理员；A=任意已登入账号；公开=匿名可访问。登入／退出由Spring Security处理，非普通Controller方法。

| 方法 | 路径 | 权限 | 用途或参数 |
|---|---|---|---|
| GET | /api/health | 公开 | 进程存活，phase为历史标签P1 |
| GET | /api/health/readiness | 公开 | 200 UP或503 DOWN |
| GET | /api/runtime/metrics | W | 固定请求与到期任务累计指标 |
| GET | /api/auth/csrf | 公开 | token、headerName及Cookie |
| POST | /api/auth/register | 公开 | JSON注册，仍需CSRF，201 |
| POST | /api/auth/login | 公开 | 表单登入，需CSRF，200 |
| GET | /api/auth/me | A | 目前身份 |
| POST | /api/auth/logout | A | 失效会话，需CSRF，204 |
| GET | /api/skus | A | 相容商品阵列，含停用，最多100 |
| GET | /api/skus/page | A | q、status=all/active/inactive、page、size |
| GET | /api/skus/{id} | A | 商品详情 |
| POST | /api/skus | W | 建立商品与库存，201 |
| PUT | /api/skus/{id} | W | 更新名称、价格、active |
| GET | /api/inventory | W | 相容库存阵列，最多100 |
| GET | /api/inventory/page | W | q、status=all/healthy/low/empty、page、size |
| GET | /api/inventory/{skuId} | W | 单商品完整库存 |
| PUT | /api/inventory/{skuId}/policy | W | 安全库存1至1000000 |
| POST | /api/inventory/{skuId}/receipts | W | 直接幂等收货，200 |
| GET | /api/inventory/ledger | W | q、type、skuId、beforeId、limit、from、to |
| GET | /api/inventory/alerts | W | q、threshold、beforeId、limit |
| GET | /api/inventory/export | W | q、status；CSV最多10000行 |
| GET | /api/orders | B | 本人相容订单阵列，最多100 |
| GET | /api/orders/page | B | q、status、page、size、from/to、minTotal/maxTotal |
| GET | /api/orders/export | B | 与订单筛选相同，无page/size |
| POST | /api/orders | B | 幂等下单，201或200 |
| GET | /api/orders/{id} | B | 本人订单详情 |
| POST | /api/orders/{id}/confirm | B | 未到期待确认单确认 |
| POST | /api/orders/{id}/cancel | B | 待确认单取消并释放预留 |
| POST | /api/orders/{id}/complete | B | 已发货单收货 |
| GET | /api/fulfillment/orders | W | 相容全部订单阵列，最多100 |
| GET | /api/fulfillment/orders/page | W | 全量筛选分页，参数同订单/page |
| GET | /api/fulfillment/orders/export | W | 全量筛选CSV |
| GET | /api/fulfillment/orders/{id} | W | 任意订单详情 |
| POST | /api/fulfillment/orders/{id}/ship | W | 已确认单发货 |
| GET | /api/dashboard | A | 按角色汇总 |
| GET | /api/dashboard/trends | A | 最近七自然日；买家本人订单 |
| GET | /api/audit | W | q、action、page、size、from、to |
| GET | /api/inventory/reconciliation | W | q、mismatchOnly、page、size |
| GET | /api/stocktakes | W | q、status、page、size、from、to |
| POST | /api/stocktakes | W | 建立单商品盘点草稿，201或200 |
| GET | /api/stocktakes/{id} | W | 盘点详情 |
| POST | /api/stocktakes/{id}/confirm | W | 检查版本并确认盘点 |
| POST | /api/stocktakes/{id}/cancel | W | 取消草稿 |
| GET | /api/receipts | W | q、status、page、size |
| POST | /api/receipts | W | 建立多行入库草稿，201或200 |
| GET | /api/receipts/{id} | W | 入库详情 |
| POST | /api/receipts/{id}/confirm | W | 整单确认入库 |
| POST | /api/receipts/{id}/cancel | W | 取消入库草稿 |
| GET | /api/returns | B/W | q、status、page、size；B只看本人 |
| POST | /api/returns | B | 本人已完成订单整单申请，201或200 |
| GET | /api/returns/{id} | B/W | B本人，W全部 |
| POST | /api/returns/{id}/confirm | W | 实物核验后整单回库 |
| POST | /api/returns/{id}/cancel | B | 本人取消待处理申请 |

相容阵列接口只返回最近或受限记录，不用于完整业务统计。前端新列表优先用/page；接口示例见仓库 `examples/stockflow.http`。本文件是人工核对的接口契约，没有宣称已安装Swagger或生成OpenAPI服务。
