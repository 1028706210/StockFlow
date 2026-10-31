# 商品安全库存与订单时间线验收

日期：2026-10-26。目录 D:/project/StockFlow；沿用现有 codex/p0-order-inventory 分支，保留前几轮未提交成果。

## 实际改动

- inventory.safety_stock 默认10，1..1000000。仓管/管理员 PUT /api/inventory/{skuId}/policy 保存；买家403。只修改规则，不改实存/预留，不写库存流水。
- 概览低库存总数、库存列表状态和默认预警查询读取该规则。库存列表包含停用商品；预警总数只统计在售商品。
- 默认 alerts 顶层 threshold=null，行增加 safetyStock、有效 threshold、shortage。可选 threshold 参数仍支持临时覆盖查询，不保存覆盖值。UI 只使用商品已保存规则。
- 预警页显示安全线与建议补货；新入库预填差额，重试已有入库保持原数量和操作标识。前端提供库存行、预警页、商品抽屉的规则入口。
- 订单详情增加 createdAt、confirmedAt、shippedAt、completedAt、cancelledAt。数据库时间随状态同事务更新；幂等重试不重写时间。发货失败回滚也不留下成功时间。
- 创建时间复用已有数据。旧订单缺失阶段时间保留NULL，UI 标注“时间未记录（历史订单）”；未发生阶段显示“等待处理”。取消分支说明原因及预留释放。
- 桌面横向步骤、手机纵向步骤，沿用深色主题及现有组件，无新增依赖。

## 自动化验证

- Java/MySQL RED：63 tests，新增7项失败、0错误；既有56项通过。证据：本机工作区 .stockflow-work/business-red.log。
- Java/MySQL GREEN：63 tests，0 failures/errors/skips，BUILD SUCCESS，2026-10-26 19:42:21+08:00。证据 .stockflow-work/business-green.log。
- 新测试覆盖：保存/重复保存、规则不改库存/流水、角色和非法字段、全量超过100条、等于安全线、停用商品、临时覆盖不保存、完整时间顺序和重放、买家隔离、取消/超时、失败发货及历史NULL。
- 前端 RED：52 tests，其中新增5项失败，原47项通过。GREEN：56 tests全部通过，含4项时间线模型测试；补货预填/重试保留、按商品查询、差额展示、非法输入和保存中切换保护。
- 打包 BUILD SUCCESS（跳过已通过的Java测试），19:43:44完成，.local/business-package.log。JAR 中静态资源与源码逐项核对。
- git diff --check 通过；未声明远程CI已执行。

## 数据库升级

V003 仅增5列，未删除业务数据。Update-LocalDatabase.ps1 顺序执行升级文件，作用于私有3308的 stockflow_dev / stockflow_test。
实际重复执行升级成功，开发库升级前后所核对的商品数12、订单数10、流水数36及演示商品规则20保持一致。历史阶段时间未回填。课程 zzyl10 未操作。

## 实际浏览器验收

使用 Computer Use / IAB 操作实际打包JAR和MySQL，未模拟接口。

1. warehouse 查找 DEMO-MAINT-20261026（雾蓝随行杯），实存12、预留0、可用12。
2. 安全库存10改为20；列表立即显示低库存。预警显示安全线20、补货+8；重新登录后仍读取20。
3. 点击补货，数量预填8，真实提交后实存/可用20；等于安全线，该商品预警消失。
4. buyer 创建一件该商品订单 SF-10，金额39.90；19:46:06创建、19:46:25确认、warehouse 19:47:21发货、buyer 19:47:38收货。实际弹窗四节点均显示真实时间。
5. 查看历史取消订单 SF-3，原创建时间保留，取消时间标注历史未记录。
6. 手机尺寸覆盖390×844；页面实际内容宽375（扣除滚动条），scrollWidth==clientWidth；订单弹窗341==341、规则弹窗357==357。订单明细表可独立横向滚动，整体页面/弹窗无横向溢出。验证后重置尺寸。
7. 浏览器 error/warn 日志为空。本轮只创建上述一笔演示订单并追加8件入库，最终该商品实存19、预留0、安全库存20（订单已完成，所以可用19，重新进入预警属于预期）。

截图：
- docs/screenshots/business-alerts.png
- docs/screenshots/business-timeline.png
- docs/screenshots/business-mobile-timeline.png
- docs/screenshots/business-mobile-policy.png

## 边界

这是单仓本地学习项目。建议补货是当前库存差额，不是销量预测或自动采购；时间线不是物流轨迹。未接真实支付、物流、邮件/推送；没有公网部署。尚未声称广泛浏览器或无障碍认证。源码未提交、未推送GitHub。
