# 运行状态与故障排查设计

目标：管理员在现有工作台查看真实运行状态与库存核对结果，识别订单超时扫描失败并得到排查入口。用户已委托常规实现与本地验证，本阶段沿用现有架构自主推进。

## 方案与取舍

采用现有RuntimeMetrics、数据库就绪探针及只读对账，新增管理员状态接口与原生JS页面。单独引入Prometheus/Grafana会增加当前本机演示的部署成本；自建告警表和发送队列也没有已确定的渠道需求。本阶段不引入依赖、数据库表或外部消息发送。

## 契约

- GET `/api/admin/runtime`仅ADMIN可访问，返回observedAt、databaseStatus（UP/DOWN）、expiryEnabled、expiryDelayMillis及metrics。
- metrics保留原requests/expiry计数字段，增加process.startedAt/uptimeSeconds，以及expiry.lastBatch（status、startedAt、completedAt、processed、failed、queryFailed）、lastSuccessfulAt、lastFailureAt。
- 批次状态为NEVER/RUNNING/SUCCEEDED/PARTIAL_FAILED/FAILED。空批次正常完成；候选处理成功只是未抛异常，不冒充实际取消订单数。
- 通过finally结束每个批次，扫描查询失败与单订单失败分开记录；后续成功恢复最近状态，同时保留历史失败时间。
- 运行状态页只给管理员；仓管既有metrics权限保持不变。页面分别读取状态及现有对账接口，允许部分失败，失败区域清空旧结果、明确无法核验；退出/切号/更新请求竞争不能回写旧状态。
- 手动刷新，不自动轮询。HTTP计数自组件启动累计、近似并发快照、包含诊断请求；历史累计5xx不当作当前持续故障，平均耗时不是P95。
- 复用现有Cookie、CSRF和身份版本核验。数据库故障可能先导致身份校验503；此时显示无法核验，不承诺管理员页面在数据库断开时仍可鉴权。外部无Cookie就绪探针仍可返回503。
- 无密码、连接串、主机路径、异常堆栈、订单明细或账号清单进入运行状态响应。

## 验收

确定时钟的批次成功/失败/恢复测试；真实HTTP角色权限与响应字段检查；前端部分失败/旧请求/退出清空回归；完整Java/Node回归、Edge桌面和320像素视口、本机运行与双仓库/新提交CI核对。

不停止现有数据库制造故障，不改演示账号；故障路径在专用测试与可控依赖中验证。外部告警、生产监控、密码找回和生产HTTPS仍待后续明确条件。
