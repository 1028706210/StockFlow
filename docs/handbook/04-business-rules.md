# StockFlow 业务规则

## 库存模型

BR-01 每种商品在唯一仓库有一行库存。H表示实存onHand，R表示预留reserved，A表示可用available。始终要求 `0 <= R <= H <= 1000000000`，`A = H - R`。预留是尚未发货的订单占用；确认订单不再增加一次预留。

| 流水类型 | 业务事件 | H变化 | R变化 |
|---|---|---|---|
| INBOUND | 直接收货或入库单确认 | +q | 0 |
| RESERVE | 下单 | 0 | +q |
| RELEASE | 待确认取消或到期 | 0 | -q |
| SHIP | 仓管发货 | -q | -q |
| ADJUST_IN | 盘盈 | +q | 0 |
| ADJUST_OUT | 盘亏 | -q | 0 |
| RETURN_IN | 整单退货确认 | +q | 0 |

流水quantity一律是正数，方向由operationType决定。available为查询计算值，不额外储存，以免维护第三个可分歧余额。

## 订单状态

BR-02 订单主路径为 `PENDING_CONFIRM → CONFIRMED → SHIPPED → COMPLETED`；仅PENDING_CONFIRM可以转CANCELLED。取消原因USER与EXPIRED分别表示本人取消与到期。不存在付款、退款、部分发货或重新开单状态。

| 操作 | 操作者 | 前置状态 | 成功后状态 | 已成功重试 |
|---|---|---|---|---|
| 建立 | 买家 | 在售且可用充足 | PENDING_CONFIRM | 同键同内容回既有订单 |
| 确认 | 本人 | 待确认且未到期 | CONFIRMED | 已确认／发货／完成回目前状态 |
| 取消 | 本人 | 待确认 | CANCELLED | 已取消回目前状态 |
| 发货 | 仓管／管理员 | 已确认 | SHIPPED | 已发货／完成回目前状态 |
| 收货 | 本人 | 已发货 | COMPLETED | 已完成回目前状态 |
| 到期取消 | 系统任务 | 待确认且已到期 | CANCELLED | 已非待确认则不再释放 |

BR-03 确认期限由数据库建立时设定为15分钟。扫描默认每批完成后等待30秒，每次最多100笔；扫描游标会绕回，单笔失败不阻挡后续订单。到期不代表画面于整点立刻更新，数据库状态和任务处理存在扫描延迟。

BR-04 每单至多10种SKU，每种合并1至99件，原始明细最多990行。重复SKU会合并并按SKU排序。金额为各明细quantity乘unitPrice之和；前端传入单价、总额、身份、状态等未定义字段会被拒绝。

## 幂等与重试

BR-05 requestKey／operationKey格式为1至64位ASCII字母数字及 `._:-`，首位必须是字母或数字。一次业务意图一个键；收到不确定结果时，先查询或重试同键同内容，不自动换键。

| 业务 | 键的范围 | 内容比对 |
|---|---|---|
| 订单 | buyerId加requestKey | 规范化SKU与数量的SHA-256 |
| 直接收货 | 流水类型、actorId加operationKey、SKU | 数量 |
| 盘点建立 | actorId加requestKey | SKU、目标数量、trim后原因 |
| 入库单建立 | actorId加requestKey | trim后来源、排序SKU与数量的SHA-256 |
| 退货建立 | buyerId加requestKey | 原订单号与trim后原因的SHA-256 |

同键不同内容返回409 IDEMPOTENCY_CONFLICT。幂等返回目前资源状态，不保证重播最初那次HTTP原文；成功后修改商品价钱不会重新计算既有订单。

## 入库与退货

BR-06 入库单状态为DRAFT、CONFIRMED、CANCELLED。来源必填且最多240字符，1至10行不重复SKU，每行1至1000000件。草稿不增加库存，未提供草稿编辑接口；内容需更正时取消原草稿再建立。确认按整单事务执行，已确认不可撤销。

BR-07 退货仅限本人COMPLETED订单的全部SKU及原数量，原因必填且最多240字符。状态为REQUESTED、CONFIRMED、CANCELLED；REQUESTED由本人取消或仓管确认。取消后可用新键重新申请；只要有REQUESTED或CONFIRMED，就不能再建立另一张有效退货。原订单保持COMPLETED。

仓管确认表示整单商品已收到且可再次销售。系统没有部分收货、不可售库存、拒绝单、退款、费用、凭证或退货期限配置；不满足条件时不要强行确认。后端没有结构化的验收勾选字段。

## 盘点与对账

BR-08 盘点草稿保存商品快照、实存、预留与version；确认目标0至1000000000且不得低于当前预留。version不符返回STOCK_CHANGED；目标低于预留返回RESERVED_STOCK。零差异可确认并记审计，但不增加零数量流水。

BR-09 对账比较三组数据：库存H与累计物理流水；库存R与累计预留流水；库存R与PENDING_CONFIRM、CONFIRMED订单明细合计。任何一组不等即不一致。差异列表默认只显示不一致；查询只揭示，不修改或重建数据。

## 快照与查询口径

BR-10 新订单、入库与盘点保存必要商品快照；退货复制原订单快照。V005以前订单的名称与编码保持NULL，显示「历史商品名称未知」，禁止回填现名作历史事实。流水显示名称来自目前商品表，不是历史名称快照。

BR-11 安全库存默认10，支援1至1000000。只有在售且available严格低于各自安全线才进预警；等于安全线属健康。库存列表low表示0小于available小于安全线，empty表示available小于等于0；因此「低库存列表」与含缺货的「预警列表」数量可能不同。

BR-12 所有列表关键字至多120字符；日期from、to包含首尾自然日，按created_at过滤；金额界限可含等号，最多两位小数且非负。入库单和退货列表目前没有日期范围参数。订单总额包含取消订单，七日入库趋势只合计INBOUND、出库只合计SHIP，不包含退货与盘点变动，不能直接当成完整库存收支或财务营收。

## 审计范围

目前记录SKU_CREATE、SKU_UPDATE、STOCK_POLICY、STOCKTAKE_CREATE／CONFIRM／CANCEL、RECEIPT_CREATE／CONFIRM／CANCEL、RETURN_CREATE／CONFIRM／CANCEL。订单履约透过订单时间字段与库存流水追踪，直接收货透过流水追踪；登入、所有读取以及所有订单操作没有统一进operation_audit。数据库内审计也不是防窜改储存。
