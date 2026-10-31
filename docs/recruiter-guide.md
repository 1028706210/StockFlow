# StockFlow · 五分钟演示与技术导读

这份导读对应 Java 单仓库存与订单履约主项目。数字素材商城是独立附属模块，不参加下面的库存演示。先看 [README](../README.md)，需要深入时再按本页进入源码。

## 一分钟介绍

StockFlow 处理一个仓库里商品从入库、被订单预留、发货到退货回库的过程。难点是重复请求、状态竞争和库存变化同时发生时，订单、余额与流水仍然一致。实现采用 Spring Boot、MyBatis 与 MySQL 事务，使用条件更新、订单行锁、内容指纹、唯一键和版本快照；买家、仓管与管理员权限由服务端校验。前端为原生 ES Modules，随 JAR 部署。

这段说明项目内容，不替代个人职责。面试时应说明自己实际编写、修改和验证的部分。

## 五分钟演示

先在独立演示环境运行，准备两个浏览器用户配置或无痕窗口分别登录 `warehouse` 与 `buyer`，避免反复退出切号。不要将账号口令截图或提交仓库。

新建专用商品，编码采用新的 `DEMO-日期-序号`，价格设为 25.00，安全库存设为 10。不要修改已有业务商品。下表是假设只有本演示操作该商品时的**预期值**，不是新产生的验收记录。

| 时长 | 操作 | 实存 H / 预留 R / 可用 A | 展示重点 |
|---|---|---|---|
| 0:00—0:40 | 仓管新建商品，用正式入库单入库 20 件 | 20 / 0 / 20 | 草稿不改库存，确认才生效；有单据、流水和审计 |
| 0:40—1:30 | 买家选择该商品 2 件下单 | 20 / 2 / 18 | 金额由后端计算；下单是预留，没有支付行为 |
| 1:30—2:10 | 买家确认；仓管核对并发货 | 确认后不变；发货后 18 / 0 / 18 | 角色分工与状态机；发货扣实存并释放预留 |
| 2:10—3:00 | 买家确认收货，申请整单退货 | 18 / 0 / 18 | 申请本身不回库，原订单保持已完成 |
| 3:00—3:50 | 仓管模拟实物验收，确认整单可再次销售后回库 | 20 / 0 / 20 | 退货不是退款；多行回库与流水同事务 |
| 3:50—5:00 | 查看该商品流水、审计和只读对账，再打开测试源码 | 无额外变化 | 业务动作可追溯；对账零差异不等于实物已经盘点 |

有时间再展示一个失败场景：创建盘点草稿后让库存发生变化，再确认旧草稿，系统应拒绝；重新盘点后才能确认。不要为了演示直接改数据库，也不要通过重复真实下单冒充网络重试测试。

已执行过的浏览器链路及单号见 [2026-10-30 验收记录](evidence/2026-10-30-lifecycle.json)，其未覆盖项仍保留。

## 面试追问与源码入口

| 追问 | 可以说明的实现 | 直接阅读 |
|---|---|---|
| 为什么需要实存和预留两个数量？ | 下单锁定可售份额，发货才离开仓库；可用量 H−R，库存约束 H≥R≥0 | [InventoryMapper](../src/main/java/io/github/stockflow/inventory/InventoryMapper.java) |
| 两个请求同时买最后一件？ | `UPDATE ... WHERE on_hand-reserved >= quantity` 的影响行数决定是否预留成功；后行失败回滚整单 | [OrderService](../src/main/java/io/github/stockflow/order/OrderService.java)、[并发测试](../src/test/java/io/github/stockflow/StockFlowIntegrationTest.java) |
| 为什么仅有 requestKey 还不够？ | 同键不同内容不能被误认为成功重放，需规范化行项目并计算指纹；唯一约束处理并发创建 | [OrderLines](../src/main/java/io/github/stockflow/order/OrderLines.java)、[订单模型测试](../src/test/java/io/github/stockflow/order/OrderLinesTest.java) |
| 确认与超时取消同时发生？ | 对订单行加锁后判断状态；确认同时校验截止时间，竞争以事务结果为准 | [OrderService](../src/main/java/io/github/stockflow/order/OrderService.java)、[履约测试](../src/test/java/io/github/stockflow/FulfillmentIntegrationTest.java) |
| 数量变过又改回，旧盘点能继续吗？ | 不能仅对比数量；version 记录中间变更，防止旧快照确认 | [StocktakeService](../src/main/java/io/github/stockflow/operations/StocktakeService.java) |
| 隐藏仓管菜单就安全了吗？ | 页面只是体验，服务端仍校验角色和归属；他人资源按404隐藏存在性 | [SecurityConfiguration](../src/main/java/io/github/stockflow/auth/SecurityConfiguration.java)、[认证测试](../src/test/java/io/github/stockflow/AuthenticationIntegrationTest.java) |
| 为什么没用 Redis、MQ、JWT？ | 当前单仓同源单体边界内，MySQL 事务和会话已经能解决问题；新增组件需有实际场景和验收标准 | [系统设计](handbook/03-system-design.md)、[前端演进决策](decisions/001-frontend-evolution.md) |
| 性能测量说明了什么？ | 只说明给定单机与合成数据的查询样本；没有生产 QPS、P99 或十万用户证据 | [原始数据](evidence/2026-10-27-large-data.json)、[验收解释](handbook/08-testing-acceptance.md) |

## 运行与排障

### 推荐：Docker

安装并启动 Docker Engine / Docker Desktop 的 Linux 容器环境与 Compose V2，并安装 PowerShell 7；Windows/Linux/macOS 都用下面的 `pwsh` 入口，在项目根目录运行：

```powershell
pwsh -File ./tools/Start-Container.ps1
```

首次生成 `.local/compose.env`，随后复用，既有账号不会在重启时重置密码。默认地址是 http://127.0.0.1:8083；首次可传 `-Port 8090`。已有环境复用保存端口，脚本不会因再次传不同端口而悄悄改配置。进程环境变量优先于文件，若覆盖了 `STOCKFLOW_HTTP_PORT` 或口令，以环境值及脚本打印地址为准。

```powershell
docker compose --env-file .local/compose.env ps
docker compose --env-file .local/compose.env logs --tail=80 app
docker compose --env-file .local/compose.env down
```

`down` 不带 `-v`，保留业务数据；不要为解决登录问题删除数据库卷。若容器就绪超时，检查 Docker、端口与应用日志；“容器正在运行”不代表 Flyway 和数据库已经就绪。

### 备选：Windows 本机 Java / MySQL

需要 Java 17+、Maven、MySQL Server 8 和 PowerShell 7。已有脚本使用独立的本地 MySQL 目录，不改课程库 `zzyl10` 或原 Windows MySQL 服务。

```powershell
pwsh -File ./tools/Start-StockFlow.ps1 -Rebuild
# Maven 不在 PATH 时，追加 -Maven '你的Maven目录\bin\mvn.cmd'
```

地址 http://127.0.0.1:8082；初始演示口令见 `.local/demo-login.txt`。使用这种方式，不应读取容器的 compose.env 当成本机账号口令。

### 检查与限制

- `/api/health` 是存活检查；`/api/health/readiness` 返回 `UP` 才说明数据库就绪探针通过。
- 新账号公开注册固定为买家。管理员演示账号已有角色，尚无用户权限维护后台。
- Java/MySQL 集成测试会清理**专用测试库**，仅按[测试说明](handbook/08-testing-acceptance.md)配置后执行，不能对开发库运行。
- 当前配置用于本地演示，默认只绑定回环地址。公开仓库不等于已经公开部署；HTTPS、生产限流、安全审计和备份恢复仍需单独完成。

## 简历可用表述

**StockFlow｜单仓库存与订单履约系统**  
Java 17 / Spring Boot / Spring Security / MyBatis / MySQL / Flyway / Docker / GitHub Actions

- 实现入库、下单预留、取消释放、发货扣库、收货及整单退货闭环，以条件更新、行锁和事务保证多商品订单与库存流水一致。
- 使用请求键、内容指纹和唯一约束处理重复请求；引入库存版本快照，拒绝库存变更后的过期盘点确认。
- 实现角色与订单归属校验、Cookie 会话和 CSRF 防护；通过真实 MySQL HTTP 集成测试覆盖并发、回滚和权限边界，使用 CI 验证测试与容器运行。

请将“实现”改为符合自己投入的“负责实现”“参与实现”或“学习并改进”，并准备相应代码讲解。不要将附属 Node 商城的测试数量计入 Java 后端成绩。
