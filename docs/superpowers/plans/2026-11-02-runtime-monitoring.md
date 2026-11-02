# 运行状态阶段实施计划

> 执行方式：superpowers:executing-plans，在当前独立发布副本按任务实现，最后进行一次独立只读审查。

目标：管理员可查看真实进程、数据库、超时扫描及库存核对状态。

架构：复用现有健康探针、RuntimeMetrics及对账接口；Java单体与原生JS，不增加依赖或数据库表。

设计：[设计说明](../specs/2026-11-02-runtime-monitoring-design.md)。基准c98f772，分支codex/runtime-monitoring；D盘原分支保留。

## 约束与审查重点

- 全部默认文案与文档简体中文；不改变库存、订单或身份规则。
- 避免历史错误冒充当前故障、失败后旧绿灯、停用任务冒充健康、退出后异步回写以及内部信息泄漏。
- 不执行外部消息发送，不切生产，不停止数据库制造故障。

## 任务1：批次生命周期与管理员诊断接口

文件：RuntimeMetrics.java、RuntimeController.java、OrderExpiration.java；RuntimeProbeTest.java、RuntimeHealthIntegrationTest.java及runtime/RuntimeMetricsTest.java。

接口：保留原metrics计数；增加设计约定的process和expiry.lastBatch/历史时间字段。新增GET /api/admin/runtime，仅ADMIN。

- [x] 先写失败测试：查询失败完成FAILED、候选失败PARTIAL_FAILED、空批次成功、失败恢复保留历史时间、任务禁用不扫描、401/403/200角色及响应无敏感字段。
- [x] 运行专项，确认因缺少新行为失败。
- [x] 实现批次开始/结束，finally清理，状态快照及管理员接口。
- [x] 运行 `mvn -Dtest=RuntimeProbeTest,RuntimeMetricsTest,RuntimeHealthIntegrationTest test`，应全部通过。

## 任务2：管理员运行状态页面

新增assets/ui/runtime-monitor.mjs、runtime-monitor.test.mjs；修改app.js、index.html、storefront.css及app-flow.test.mjs。

接口：createRuntimeMonitor({document,getSession,request,ui})返回show/refresh/reset；使用任务1接口和现有GET /api/inventory/reconciliation?size=1。

- [x] 先写失败测试：正确显示批次和历史计数、部分失败清空对应区域、较晚旧响应和退出不能回写、非管理员路由不请求、手动刷新不重复启动同一在途检查。
- [x] 运行Node专项，确认缺少模块/行为失败。
- [x] 实现独立模块、管理员导航与页面、响应式布局，复用已有工具。
- [x] 运行全部Node与Java verify，必须无失败；未启用专项如实记录。

## 任务3：运行、审查与交付

- [x] 独立只读审查，重要问题先失败回归后修复；记录裁定。
- [x] 同步明确文件到D盘、仅重启经核对归属的8096展示Java，保留数据。
- [x] Edge管理员/仓管权限与手机页面验收，保存截图。
- [x] 更新需求/API/运维/验证记录，准备只含源码、文档和脱敏截图的发布清单。双仓库、新CI及归档结果以最终交付核对为准。
