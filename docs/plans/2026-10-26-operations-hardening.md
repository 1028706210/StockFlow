# StockFlow 业务与交付完善

用户已授权按建议实施，提供远程仓库 https://github.com/1028706210/StockFlow 用于CI验证。沿用 D:/project/StockFlow 原有成果与分支，不删除或覆盖用户改动。

- [x] 表格复制/清筛选/页容量、固定表头、字体与异常态统一。
- [x] 按角色的待办与真实7天业务趋势。
- [x] 商品创建/变更、安全库存和盘点的事务内审计，记录actor、前后值、requestId，不保存凭据。
- [x] 只读库存对账：实存对流水，预留对流水与待确认/已确认订单。
- [x] 盘点单：幂等创建、DRAFT确认/取消、版本校验避免覆盖变化库存、实盘不能低于预留、调整流水和审计同事务。
- [x] 405/415映射和已打包JAR启动不依赖Maven。
- [x] 可复现启动、Compose配置、真实CI、SQL执行计划与并发性能证据。
- [x] 测试、打包、浏览器桌面/手机验收、文档与独立分支推送。

V004只增表与列，保留现有数据。inventory.version在所有物理/预留变动时递增；规则修改不递增。盘点确认锁盘点记录再锁单SKU库存；订单仍按升序锁库存。流水新增ADJUST_IN/ADJUST_OUT和stocktakeId。新数据均为专用演示记录，不操作课程项目/zzyl10。

验收与限制见 docs/operations-hardening-qa.md；CI容器验证由GitHub Linux runner完成，本机未安装Docker。
