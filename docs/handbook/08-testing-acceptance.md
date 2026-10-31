# StockFlow 测试与验收

## 验证分层

单元与前端状态测试覆盖规范化、查询恢复、身份隔离和重试；真实HTTP加MySQL测试覆盖鉴权、事务、锁竞争、幂等和数据库约束；迁移测试覆盖空库、旧库和受损库；CI另建Docker实例检查可运行性。浏览器实操独立验证可用性，不以API200代替点击、焦点和手机布局。

## 需求追踪与最小验收矩阵

| 需求 | 关联规则 | 自动化依据 | 人工重点 |
|---|---|---|---|
| FR-01／02 账号权限 | 身份与归属 | AuthenticationIntegrationTest、FulfillmentIntegrationTest、DocumentIntegrationTest | 退出后失效、换账号不显示旧数据 |
| FR-03 商品 | BR-10 | WorkbenchIntegrationTest、RevisionIntegrationTest | 建立、改价、停用与历史快照 |
| FR-04／05 库存下单 | BR-01／04／05 | StockFlowIntegrationTest、OrderLinesTest | 不足时整单失败、重试不重复 |
| FR-06 履约到期 | BR-02／03 | FulfillmentIntegrationTest、RuntimeProbeTest | 确认、发货、收货、取消和到期 |
| FR-07 历史 | BR-10 | RevisionIntegrationTest、DocumentIntegrationTest | 旧明细未知提示清楚 |
| FR-08 流水预警 | BR-01／11 | InventoryVisibilityIntegrationTest、BusinessProgressIntegrationTest | 缺货与低库存口径、关联单据 |
| FR-09 盘点 | BR-08 | OperationsIntegrationTest | 过期草稿提示、零差异确认 |
| FR-10 对账审计 | BR-09／12 | OperationsIntegrationTest、RevisionIntegrationTest | 差异只读、前后值可读 |
| FR-11／12 单据 | BR-05／06／07 | DocumentIntegrationTest | 建立、取消、整单确认、实物提醒 |
| FR-13 筛选导出 | BR-12 | RevisionIntegrationTest、business-query、business-documents | 刷新／返回恢复、下载内容 |
| FR-14 工作台 | BR-12 | WorkbenchIntegrationTest、OperationsIntegrationTest | 买家本人统计、非营收标示 |
| FR-15 前端与助手 | 身份epoch | app-flow及聊天／查询状态测试 | 键盘、手机、动画偏好、中文输入 |
| FR-16 迁移运行 | 结构与依赖 | MigrationIntegrationTest、RuntimeHealthIntegrationTest、RuntimeProbeTest | 启动、故障503、指标权限 |

## 测试环境与执行

测试会清理业务表，仅允许专用stockflow_test；迁移夹具会DROP专库stockflow_test_migration中的表并检查实际库名。操作前确认都是可丢弃测试环境，不用开发数据进行压测。一般本机入口：

```powershell
node --test src/test/frontend/*.test.mjs
.\tools\Invoke-Local.ps1 -Action test -Maven '你的Maven目录\bin\mvn.cmd'
```

现有Invoke-Local会设定普通测试凭据，但不建立或设定迁移专库URL。要完整重现3项迁移测试，需由维护者事先建立可丢弃的stockflow_test_migration、授权测试账号，再设定：

```powershell
$env:STOCKFLOW_MIGRATION_TEST_DB_URL = 'jdbc:mysql://127.0.0.1:3308/stockflow_test_migration?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC'
.\tools\Invoke-Local.ps1 -Action test -Maven '你的Maven目录\bin\mvn.cmd'
# 另启用大数据场景；此场景会清理并生成测试数据
$env:STOCKFLOW_LARGE_DATA_EVIDENCE = 'true'
.\tools\Invoke-Local.ps1 -Action test -Maven '你的Maven目录\bin\mvn.cmd'
Remove-Item Env:\STOCKFLOW_LARGE_DATA_EVIDENCE
```

上述命令以当前私有测试设定档为前提。未设迁移URL会跳过迁移测试，未开大量标志会跳过1项大数据测试；不要只看BUILD SUCCESS就宣称所有测试已执行。报告在target/surefire-reports，CI依工作流启用两者并保存报告。

## 核心用例

TC-01 账号：非法名称、密码UTF-8超长、重复注册；注册不自动登入；缺CSRF拒绝；登入换会话；退出后无权读数据。

TC-02 并发下单：固定实存，让多个不同请求竞争，成功数不得超过可用；同键同内容并发只生成一单；变更内容409；后行SKU不足应全部回滚。

TC-03 履约竞争：确认与到期、取消与确认、重复发货与完成；无论结果路径都满足H与R约束，流水不重复。

TC-04 单据：入库建立重放、重复SKU、确认重放；回库后行上限或流水冲突须全单回滚；不同键同订单退货并发只保留一张有效；非本人404；取消后可新申请。

TC-05 盘点：建立后收发或预留往返也拒绝旧version；目标低于R拒绝；零差异无调整流水；确认和取消竞争有一致终态。

TC-06 筛选与导出：日期首尾、非法月份与倒置、金额等号与精度；列表和导出一致；越权不泄漏；公式字符被保护；10000成功、10001拒绝。

TC-07 迁移：空库可启动；旧数据保留且快照NULL；重启不重放；陌生或删约束库不自动baseline；无数据库时readiness503。

## 真实Edge验收清单

此清单尚待对新单据基准执行，不是通过报告。使用专门演示商品和账号，保留单号及前后库存；避免修改既有业务数据作试验。

- 桌面约1440×960：登入仓管、建立两行入库草稿、选商品、取消一张、确认另一张；验证流水RC关联和审计。
- 买家下单并确认、仓管发货、买家收货；核对快照、H、R、A及时间线。
- 本人申请整单退货、取消后重新申请；仓管核实勾选后回库；重试不增加第二次库存，对账为0差异。
- 订单日期与金额筛选后刷新、切页、返回和关弹窗，核对地址、页码、焦点；下载CSV比对筛选内容。
- 手机视口390×844：导航、长名称、日期字段、商品选择、单据明细、确认按钮、键盘及助手均可使用，不因装饰遮挡。
- 顶部动态／密度控制保持隐藏；粒子不阻挡点击；减少动态效果时尊重偏好；退出及换账号旧结果不回填。

每项记录提交、浏览器版本、视口、步骤、预期／实际、单号和证据路径。发现错误先保存最小重现与requestId，再修复并重验相同路径。

## 已有证据和限制

| 证据 | 时间与基准 | 结果与解读 |
|---|---|---|
| 常规后端本地完整套件 | 2026-10-27，33c830f功能基线 | 97个测试案例中96执行通过、1个大数据默认跳过；迁移URL已设定 |
| 前端与规则测试 | 同日同功能基线 | 89通过；状态／模型／静态流程测试不等于真实页面验收 |
| 大数据场景 | 2026-10-27 | 1000 SKU、10000订单、30000流水、10000审计，对账差异0；导出上限通过 |
| GitHub Actions | run 40480976501，33c830f；2026-10-29重读状态 | mysql-tests与container-smoke成功，包含大量数据与迁移环境 |
| 新单据Edge实操 | 前次连接受Work设定阻挡 | 尚待补验，旧版截图不能替代 |
| 公网运行／备份恢复／全量安全测试 | 无对应执行证据 | 不纳入完成声明 |

大数据5个温热HTTP样本中位数：订单深页83.67ms、流水92.77ms、审计72.95ms、对账161.19ms，包含Basic认证开销。这是单机合成数据、特定查询与分布，没有吞吐量、P95／P99或长时间稳定性结论。

证据入口：[业务可靠性记录](../business-reliability-qa.md)、[大量数据JSON](../evidence/2026-10-27-large-data.json)、[CI](https://github.com/1028706210/StockFlow/actions/runs/40480976501)。原文档整理核对既有证据；2026-10-29第一阶段另重跑89项前端测试、打包并检查匿名模块HTTP加载。没有重跑后端全套或完成新页面实操。
