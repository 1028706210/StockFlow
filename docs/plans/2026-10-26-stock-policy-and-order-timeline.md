# 商品安全库存与订单时间线

范围：独立 StockFlow；原目录和分支，保留未提交成果。

- 每个 inventory.safety_stock 默认10，1..1000000，仓管/管理员可修改。只修改规则，不改库存、不写库存流水。
- 默认预警按各商品保存规则；概览和库存状态使用同一安全线。库存状态仍可查看停用商品；预警只包括在售商品。
- GET /api/inventory/alerts 可选 threshold 保持临时覆盖兼容，不保存覆盖值。默认响应 threshold=null，每行有 safetyStock、threshold、shortage。
- PUT /api/inventory/{skuId}/policy {safetyStock}；前端从库存行、预警和详情调整规则，补货默认填差额，保留已有幂等操作。
- 订单详情增加 createdAt/confirmedAt/shippedAt/completedAt/cancelledAt，数据库时间与状态同事务更新，重复操作不更新时间。
- 旧订单缺失阶段时间保留NULL，时间线按状态标记阶段，明确历史时间未记录。
- V003 增量升级仅增列，保留现有数据；Update-LocalDatabase 按文件名顺序重复执行升级。
- 不新增依赖，不接邮件/消息推送，不接支付/物流，不操作课程数据库。

已完成：实现、63项Java/MySQL及56项前端验证、打包、桌面和手机实际验收。证据见 docs/stock-policy-timeline-qa.md。
