# StockFlow · 单仓库存与订单履约系统

[![持续集成](https://github.com/1028706210/StockFlow/actions/workflows/verify.yml/badge.svg?branch=main)](https://github.com/1028706210/StockFlow/actions/workflows/verify.yml)

**一个围绕库存一致性、订单状态和失败重试实现的 Java 后端项目。** 买家下单即预留库存，仓管发货才扣减实存；取消释放预留，收货后可申请整单退货，仓管验收后回库。入库、流水、预警、盘点、对账与审计组成可追溯的单仓闭环。

[五分钟演示与技术导读](docs/recruiter-guide.md) · [系统设计](docs/handbook/03-system-design.md) · [业务规则](docs/handbook/04-business-rules.md) · [API](docs/handbook/06-api-reference.md) · [完整文档](docs/handbook/README.md) · [Gitee 镜像](https://gitee.com/DAD323/stock-flow)

![StockFlow 库存与订单工作台，实际界面截图](docs/screenshots/operations-overview.png)

## 技术栈

| 层次 | 当前实现 |
|---|---|
| 后端 | Java 17、Spring Boot 4.1.0、Spring Security、MyBatis Starter 4.1.0、Bean Validation |
| 数据 | MySQL 8 / InnoDB、Flyway 增量迁移、条件更新、行锁、唯一约束 |
| 前端 | 原生 JavaScript ES Modules、HTML/CSS，随 Spring Boot JAR 同源部署；**不是 Vue** |
| 身份 | BCrypt 密码哈希、服务端会话、HttpOnly / SameSite Cookie、CSRF、角色与资源归属校验 |
| 工程 | Maven、JUnit、真实 HTTP + MySQL 集成测试、Docker Compose、GitHub Actions |

## 值得看代码的地方

| 问题 | 实现及取舍 | 代码与验证入口 |
|---|---|---|
| 多人同时下单，不能超卖 | SQL 条件更新校验可用量；多 SKU 预留、订单、快照与流水在同一事务内，失败整体回滚 | [库存 SQL](src/main/java/io/github/stockflow/inventory/InventoryMapper.java)、[订单事务](src/main/java/io/github/stockflow/order/OrderService.java)、[集成测试](src/test/java/io/github/stockflow/StockFlowIntegrationTest.java) |
| 请求超时，重试不能产生第二单 | 请求键配合内容指纹与数据库唯一约束；同键同内容重放，同键异内容冲突；前端保留不确定请求 | [订单规范化](src/main/java/io/github/stockflow/order/OrderLines.java)、[单据服务](src/main/java/io/github/stockflow/documents/DocumentService.java)、[单据测试](src/test/java/io/github/stockflow/DocumentIntegrationTest.java) |
| 确认、取消、到期和发货会竞争 | 锁定订单后校验状态，按 SKU 固定顺序修改库存；重复成功操作不再次扣减 | [订单服务](src/main/java/io/github/stockflow/order/OrderService.java)、[履约测试](src/test/java/io/github/stockflow/FulfillmentIntegrationTest.java) |
| 盘点期间发生收发货 | 保存库存版本快照，确认时检查 version 与预留下限，拒绝失效草稿 | [盘点服务](src/main/java/io/github/stockflow/operations/StocktakeService.java)、[盘点 SQL](src/main/java/io/github/stockflow/operations/OperationsMapper.java)、[测试](src/test/java/io/github/stockflow/OperationsIntegrationTest.java) |
| 买家不能看到他人的订单 | 身份来自服务端，会话写操作要求 CSRF；越角色接口 403，他人订单或退货 404 | [安全配置](src/main/java/io/github/stockflow/auth/SecurityConfiguration.java)、[认证测试](src/test/java/io/github/stockflow/AuthenticationIntegrationTest.java)、[买家范围验收](docs/buyer-scope-qa.md) |
| 改商品名不能改历史订单 | 明细持久化下单快照；旧数据缺失快照明确显示未知，不回填现名；Flyway 校验旧库 | [迁移 V005](db/migrations/V005__order_item_snapshots.sql)、[回归测试](src/test/java/io/github/stockflow/RevisionIntegrationTest.java)、[迁移测试](src/test/java/io/github/stockflow/MigrationIntegrationTest.java) |

采用模块化单体与同源会话；库存一致性位于一个 MySQL 事务内，没有为展示技术而引入微服务或分布式锁。

## 业务范围

| 角色 | 可以完成的业务 |
|---|---|
| 买家 | 注册登录、浏览在售商品、下单、查询本人订单、确认或取消待确认订单、确认收货、申请或取消整单退货 |
| 仓管 / 管理员 | 商品维护、正式入库、发货、验收退货、库存流水、安全库存与预警、盘点、只读对账、审计、受限 CSV 导出 |

核心库存关系：`可用量 = 实存 − 预留`，始终要求 `实存 ≥ 预留 ≥ 0`。

| 动作 | 实存 | 预留 |
|---|---|---|
| 确认入库 | 增加 | 不变 |
| 创建订单 | 不变 | 增加 |
| 取消 / 待确认超时 | 不变 | 释放 |
| 仓管发货 | 扣减 | 同步释放 |
| 买家确认收货 | 不变 | 不变 |
| 验收整单退货 | 增加 | 不变 |

订单确认是业务模拟，**不产生付款**；整单退货只处理实物回库，不含退款。当前没有真实支付、物流、多仓、部分退货、管理员用户维护或大模型客服。

## 快速运行

推荐使用 **Docker Engine / Docker Desktop（Linux 容器）+ Compose V2 + PowerShell 7**。以下脚本在 Windows/Linux/macOS 均使用 `pwsh`；构建容器时无需本机安装 Java、Maven 或 MySQL。

```powershell
git clone https://github.com/1028706210/StockFlow.git
cd StockFlow
pwsh -File ./tools/Start-Container.ps1
```

脚本生成私有随机口令，启动并等待 `/api/health/readiness` 返回 `UP` 后才报告就绪。打开 **http://127.0.0.1:8083**。

演示账号：`warehouse`（仓管）、`buyer` / `buyer2`（两个买家）、`admin`（管理员）。默认初始口令保存在本机 **`.local/compose.env` 的 `STOCKFLOW_DEMO_PASSWORD`**，不会写入公开仓库。若进程环境设置了同名变量，Compose 优先使用环境值；端口同理，以脚本实际打印地址为准。数据库已有账号时，重启不会重置密码。

```powershell
# 查看容器状态 / 停止服务；down 不带 -v，保留数据库卷
docker compose --env-file .local/compose.env ps
docker compose --env-file .local/compose.env down
```

容器端口默认 8083；已有配置沿用保存端口，首次启动可用 `-Port 8090`。本机 Java/MySQL 启动方式见[启动与排障](docs/recruiter-guide.md#运行与排障)，默认端口 8082。两种方式使用各自数据库，勿混用口令。

## 验证证据

| 范围 | 已有结果 | 如何核对 |
|---|---|---|
| 已发布版本 `f3f968f` 的 CI | `mysql-tests`、`container-smoke` 均成功，包含迁移与大数据测试配置 | [准确提交的运行记录](https://github.com/1028706210/StockFlow/actions/runs/41173280488) |
| 2026-10-31 本地发布快照 | StockFlow 前端 96 项 + 数字商城 28 项，共 124 项通过；不是 Java 测试数量 | [发布复核](docs/recruiter-readiness.md) |
| 真实 Edge 业务链路 | 入库、下单履约、收货、退货回库及两商品对账；有单号与 API 交叉核验 | [2026-10-30 原始记录](docs/evidence/2026-10-30-lifecycle.json) |
| 买家界面与隔离 | 专属导航、在售筛选、帮助及越角色路由；写流程另由 MySQL 集成覆盖 | [买家范围验收](docs/buyer-scope-qa.md) |

仓库当前的检查结果以顶部 CI 为准；历史浏览器记录只代表已记录的步骤，不是全端验收或生产性能结论。

```powershell
# 需要 Node.js 24；无需 npm install
node --test src/test/frontend/*.test.mjs digital-store/tests/*.test.mjs
```

完整后端测试需要专用测试数据库，**不要对开发库执行**。环境配置和执行方式见[测试文档](docs/handbook/08-testing-acceptance.md)及[CI 配置](.github/workflows/verify.yml)。

## 目录导读

```text
src/main/java/io/github/stockflow/  按业务领域划分的后端模块
src/main/resources/static/        原生前端、查询状态与业务交互
src/test/                         前端回归及 Java/MySQL 集成测试
db/migrations/                    数据库迁移参考，运行迁移由应用内 Flyway 执行
tools/                            容器及独立本机数据库启动脚本
docs/handbook/                    需求、设计、规则、数据库、API 与使用手册
digital-store/                    独立数字素材商城附属模块（Node.js，非 Java 主线）
```

数字商城与本项目的实物库存业务分开运行，**不计入 Java 后端能力或测试数量**。其买家端已有实现，管理员后台、Canva 买家复制交付和真实收款待完成；说明见 [digital-store/README.md](digital-store/README.md)。收费素材、原稿管理链接、密钥和数据库保持私有。

项目是学习、演示与面试作品，业务核心有实现与测试依据；不宣称线上营收、十万用户或生产高并发。简历中的个人职责应按本人实际投入表述，能解释 AI 协作部分的实现和验证。
