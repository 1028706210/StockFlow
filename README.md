# StockFlow

单仓库存与订单履约系统，面向 Java 后端学习与实习项目展示。当前实现 **P1：持久化账号与订单履约闭环**，业务核心独立编写，未复制课程项目或使用课程数据库。

## 已实现

- MySQL 账号持久化、买家注册、BCrypt 密码哈希、登录、会话恢复和退出失效。
- Spring Security 服务端会话；浏览器使用 HttpOnly / SameSite=Lax Cookie，写入操作校验 CSRF。
- 买家、仓管、管理员角色与订单资源归属校验。公开注册固定为 BUYER。
- 商品创建与查询，库存查询，带操作键的幂等入库。
- 多商品下单、规范化数量、服务端价格快照、请求内容指纹及幂等重放。
- 下单预留、确认、取消释放、超时取消释放、仓管发货扣减、买家确认收货。
- 订单状态、库存和流水在同一 MySQL 事务内更新；多 SKU 失败整体回滚。
- 工作概览、商品/库存/本人订单/仓管履约列表、状态筛选和图形化下单。
- 桌面鼠标悬停反馈与移动布局；尊重减少动态效果设置。

当前没有接入真实支付、物流、找回密码、管理员用户维护、Redis、RabbitMQ 或 outbox。确认订单是模拟业务操作，不产生支付行为。

## 技术与运行边界

Java 17、Spring Boot 4.1.0、Spring Security、MyBatis 4.1.0、MySQL 8/InnoDB、Maven、JUnit。前端使用原生 ES Modules、HTML/CSS，随 JAR 提供，无需安装前端依赖。

这是本地学习/演示配置，仅监听 127.0.0.1。标准 HTTP Basic 保留给显式设置 Authorization 的本地 API 客户端与集成测试；此类请求豁免 CSRF，浏览器界面不发送 Basic 凭据。公网部署、HTTPS、限流和生产身份安全评审不属于本次交付。

## Windows 启动与已有数据库升级

需要 Java 17+、Maven 3.6.3+、MySQL Server 8、PowerShell 7。已有 P0 数据库也执行以下升级，不需要清库：

```powershell
.\tools\Start-LocalDatabase.ps1
.\tools\Update-LocalDatabase.ps1
.\tools\Invoke-Local.ps1 -Action package
.\tools\Invoke-Local.ps1 -Action run
```

Maven 未在 PATH / MAVEN_HOME 时，为 Invoke-Local.ps1 传入 -Maven 指定 mvn.cmd。数据库脚本使用独立 MySQL 目录与凭据，不修改原 Windows MySQL 服务。

打开 [本地工作台](http://127.0.0.1:8082)。演示账号 buyer、buyer2、warehouse、admin 的初始口令见本机 .local/demo-login.txt；注册账号使用自己设置的密码。登录会话在 30 分钟无活动后过期，退出会使当前会话立即失效。

升级脚本只操作 stockflow_dev / stockflow_test，增加 app_user 和订单 cancel_reason，保留原商品、订单及演示用户 ID 1–4。新用户编号从 100 起分配；重启只插入缺失演示账号，不覆盖已有密码。SQL 位于 [V002 升级文件](db/migrations/V002__accounts_and_fulfillment.sql)；其他环境需要先审阅并应用对应升级。

开发数据库：127.0.0.1:3308 / stockflow_dev；测试数据库：同实例的 stockflow_test，使用不同数据库用户与密码。课程库 zzyl10 没有参与升级。

测试会清理 stockflow_test 的业务表，并在清理前检查实际数据库名。不要把测试环境指向业务库。口令、数据库目录、构建文件和本机证据目录均由 Git 忽略。

## 体验完整流程

1. 用 warehouse 登录，创建商品并入库。
2. 退出后，用已注册买家或 buyer 登录；选择商品下单。
3. 在“我的订单”确认订单；待确认时也可取消，取消会释放预留。
4. 用 warehouse 登录，在“订单履约”筛选待发货订单，确认发货。
5. 买家再次登录，在“我的订单”确认收货，订单变为已完成。

订单有 15 分钟确认期限。超时任务每 30 秒检查一批、每批最多处理 100 单；到期订单自动取消并释放预留。失败订单会在后续扫描重试，跨批次游标保证它们不会永久阻挡后续正常订单。

| 状态操作 | 结果 | 库存变化 |
|---|---|---|
| 创建订单 | 待确认 | reserved 增加 |
| 买家确认 | 已确认 | 无 |
| 买家取消 / 待确认超时 | 已取消 | reserved 减少 |
| 仓管发货 | 已发货 | on_hand 与 reserved 同时减少 |
| 买家确认收货 | 已完成 | 无 |

库存始终满足 on_hand >= reserved >= 0。确认后的订单不开放取消；支付退款与售后不在范围内。同一成功操作重试返回当前结果，不重复改动库存。

## API

| 请求 | 用途 |
|---|---|
| GET /api/health | 进程存活与阶段，不代表数据库健康 |
| GET /api/auth/csrf | 取得 CSRF token/headerName |
| POST /api/auth/register | JSON 买家注册，201，不自动登录 |
| POST /api/auth/login | 表单编码登录，200，创建/轮换会话 |
| GET /api/auth/me | 当前身份，匿名 401 |
| POST /api/auth/logout | 带 CSRF 退出，204 |
| GET / POST /api/skus | 查询 / 仓管创建商品 |
| GET /api/inventory | 仓管查询库存 |
| POST /api/inventory/{skuId}/receipts | 仓管幂等入库 |
| GET / POST /api/orders | 本人订单列表 / 幂等创建 |
| GET /api/orders/{id} | 本人订单详情 |
| POST /api/orders/{id}/confirm | 本人确认 |
| POST /api/orders/{id}/cancel | 本人取消 |
| POST /api/orders/{id}/complete | 本人确认收货 |
| GET /api/fulfillment/orders | 仓管/管理员订单列表 |
| GET /api/fulfillment/orders/{id} | 仓管/管理员订单详情 |
| POST /api/fulfillment/orders/{id}/ship | 仓管/管理员发货 |

浏览器会话下所有写入请求均带 X-CSRF-TOKEN。用户、角色、价格、总额、库存余额和订单状态由服务端确定，前端不能直接指定。买家访问他人订单返回 404；缺少角色返回 403；状态/库存冲突返回 409；字段非法返回 400。错误带 requestId，不返回堆栈或 SQL。

注册账号为 3–32 位小写字母/数字/下划线，以字母开头。密码为 8–64 个字符，BCrypt 输入最多 72 个 UTF-8 字节；仅保存哈希。
每单最多 10 种 SKU，同一 SKU 合并后数量为 1–99。金额由数据库商品价格快照计算。

[IntelliJ HTTP 示例](examples/stockflow.http) 使用本地 Basic 凭据变量，不在文件内保存真实口令。

## 界面与验证

![StockFlow 工作台](docs/screenshots/workbench.png)

[登录](docs/screenshots/login.png) · [注册](docs/screenshots/register.png) · [订单履约](docs/screenshots/fulfillment.png) · [本人订单](docs/screenshots/orders.png) · [下单](docs/screenshots/create-order.png) · [移动端](docs/screenshots/mobile-order.png)

鼠标停留时，按钮轻微抬升、指标卡增加阴影、导航轻微平移、输入框边框渐变。动画仅对精细鼠标且未要求减少动态效果的设备启用。

列表仅展示最近 100 条，页面内每页 8 条；指标来自当前加载数据，不是全库统计或营收统计。状态操作以服务端响应为准。

```powershell
node --test src/test/frontend/*.test.mjs
.\tools\Invoke-Local.ps1 -Action test
```

当前自动化验证：**37 项 Java/MySQL 测试、31 项前端测试通过，项目打包成功**；真实浏览器已走通账号与订单流程、定时到期释放和悬停动画。详细记录见 [执行记录](docs/progress.md) 与 [UI 验收](design-qa.md)。真实 MySQL 测试覆盖事务、并发库存、请求幂等、注册唯一性、会话/CSRF、状态竞争、重复操作、超时释放及失败回滚。测试通过只代表相应范围，不是吞吐量基准或生产认证。
本机证据位于 artifacts/p1-20261024（忽略提交）；GitHub Actions 配置存在，尚未声称远程执行成功。

[Figma/Canva 设计来源](docs/UI-DESIGN.md) · [初始业务设计](docs/specs/2026-10-22-stockflow-design.md) · [本轮设计](docs/specs/2026-10-23-account-fulfillment-design.md) · [本轮实施计划](docs/plans/2026-10-23-account-fulfillment.md)
