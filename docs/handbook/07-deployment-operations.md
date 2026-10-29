# StockFlow 部署与运维手册

## 环境和安全边界

本手册面向本机演示和接手维护。需要Java 17、Maven、MySQL Server 8与PowerShell；执行前端测试另需Node，CI用Node 24。现有启动脚本使用PowerShell较新.NET能力，Windows建议用PowerShell 7；不要假设旧版Windows PowerShell一定相容。

本机应用只监听127.0.0.1:8082，私有MySQL默认3308，开发库stockflow_dev、测试库stockflow_test；迁移破坏性夹具使用额外stockflow_test_migration。禁止把测试指向开发库或课程库zzyl10。当前只有local／test安全设定及演示账号，不提供生产部署配置，不能只改监听地址就当成安全上线。

## 第一次启动

从含完整源代码的 `codex/operations-hardening` 分支开始，在仓库根目录执行：

```powershell
# 已配置Java、Maven与MySQL路径时
.\tools\Start-StockFlow.ps1
# Maven或MySQL不在默认位置时，以本机实际路径替换
.\tools\Start-StockFlow.ps1 -Rebuild -Maven '你的Maven目录\bin\mvn.cmd' -MySqlHome '你的MySQL Server 8目录'
```

Start-LocalDatabase会启动独立数据目录，第一次建立库、账号与随机口令；端口被其他服务占用、已有数据无对应凭据时拒绝重建。首次建立的是私有演示环境，不修改原Windows MySQL服务。

程序随后打包（缺JAR或-Rebuild时）并启动，Flyway在启动中升级结构。浏览器访问 `http://127.0.0.1:8082/`。演示账号buyer、buyer2、warehouse、admin的本机初始口令在忽略提交的 `.local/demo-login.txt`；已存在账号的哈希不会因重启而重设，因此修改环境变量不是现有账号改密流程。

若要分步执行：

```powershell
.\tools\Start-LocalDatabase.ps1
.\tools\Invoke-Local.ps1 -Action package -Maven '你的Maven目录\bin\mvn.cmd'
.\tools\Invoke-Local.ps1 -Action run
```

`Update-LocalDatabase.ps1`目前只是相容提示入口，不会重放SQL。Windows打包若提示JAR无法改名，先关闭正在执行此StockFlow JAR的预览进程，再打包；不要结束所有Java程序。前台启动视窗按Ctrl+C停止应用。私有MySQL可在确认无其他用户后执行 `Invoke-Local.ps1 -Action stop-db`，脚本会校验程序归属，不删数据。

## 配置项

| 配置 | 用途与默认 |
|---|---|
| STOCKFLOW_DB_URL／USERNAME／PASSWORD | 开发数据库；URL默认127.0.0.1:3308/stockflow_dev，密码必须提供 |
| STOCKFLOW_DEMO_PASSWORD | 缺少演示账号时初始化哈希；至少12字符且不超72 UTF-8字节 |
| STOCKFLOW_TEST_DB_URL／USERNAME／PASSWORD | 测试库连接，仅测试profile读取 |
| STOCKFLOW_MIGRATION_TEST_DB_URL | 迁移夹具专库URL；未设定则3项迁移测试跳过 |
| STOCKFLOW_LARGE_DATA_EVIDENCE | true时启用大量合成测量；会清理测试数据 |
| stockflow.expiration.enabled | 默认true；test profile关闭定时扫描 |
| stockflow.expiration.delay-ms | 默认30000，固定延迟批次扫描 |
| server.port／server.address | 应用默认8082／127.0.0.1，可用Spring环境变量覆盖 |
| Hikari设定 | 最大12连接，取连接3秒，验证2秒，默认READ_COMMITTED |

私密档案 `.local/database.json`、`.local/compose.env`、`.local/mysql-admin.cnf` 和demo-login.txt不应贴入文档、公开工单或截图。检查Git忽略规则后再提交。程序没有提供密钥管理平台或自动轮换服务。

## Docker Compose

需要Docker Desktop的Linux容器或Linux Docker。执行 `.\tools\Start-Container.ps1`，首次生成 `.local/compose.env`，启动MySQL 8.0.43及非root Java容器。默认宿主机地址为 `http://127.0.0.1:8083`，MySQL没有宿主机端口映射。

```powershell
docker compose --env-file .local/compose.env ps
docker compose --env-file .local/compose.env logs --tail=100 app
docker compose --env-file .local/compose.env down
```

down保留命名数据卷；不要加删卷选项。脚本中的-Port只在首次生成env时写入，已有env不会随参数自动重写。GitHub Linux runner已有真实构建／启动／接口成功记录；这不代表用户本机Docker已验收。

## 数据库升级与回退

1. 先确认目标库、当前应用提交和Flyway版本，停写并备份。
2. 保留已执行迁移档案不变；新变更新增后续版本。
3. 空库执行B004基线和V005、V006；没有历史表的已知V004旧库先校验字段、键、约束及表集合，符合才登记baseline 4后升级。
4. 未知结构、约束缺失、校验失败时停止启动，保留原库供核对；禁止手工强设baseline或clean以掩盖差异。
5. 启动后检查就绪、数据数量和对账，再走核心流程。

Flyway clean已禁用，系统没有自动down migration。应用回退前必须判断旧JAR是否相容新结构；需要数据回退时，在隔离环境恢复备份并验证，不直接覆盖现有库。

## 备份与恢复建议

以下是需要执行的运维程序，不是已通过的恢复演练。备份前确认无DDL与业务写入；使用有最小必要权限的本机账号和权限受限的client设定档。可用既有私有实例管理设定汇出：

```powershell
# 在仓库根目录；只备份stockflow_dev，不包含私密设定档
$taskMysqlBin = '你的MySQL Server 8目录\bin'
$taskBackupDir = Join-Path (Get-Location) 'artifacts\backups'
New-Item -ItemType Directory -Force -Path $taskBackupDir | Out-Null
$taskBackupFile = Join-Path $taskBackupDir ('stockflow_dev-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.sql')
& (Join-Path $taskMysqlBin 'mysqldump.exe') "--defaults-extra-file=$((Get-Location).Path)\.local\mysql-admin.cnf" --single-transaction --no-tablespaces --set-gtid-purged=OFF stockflow_dev "--result-file=$taskBackupFile"
if ($LASTEXITCODE -ne 0) { throw '备份失败，不能作为恢复点' }
```

备份含业务与账号哈希，仍属需保护数据，不提交Git。恢复演练应由维护者建立独立恢复库，汇入后比对关键表数量、Flyway记录、订单明细及对账，保留结果与恢复耗时；不得使用测试清理脚本操作恢复数据。未实测前不填RPO／RTO承诺。

## 健康检查与排障

GET `/api/health`只证明进程可响应；GET `/api/health/readiness`为UP或DOWN，失败503且不公开依赖详情。`/api/runtime/metrics`需仓管／管理员，累积请求数、耗时、HTTP分类与到期批次。重启归零，没有外部时序存储、通知或告警平台。

| 现象 | 优先检查 | 处理方向 |
|---|---|---|
| 3308被占用 | 私有实例是否已存在、管理设定能否验证 | 不覆盖别的MySQL；核对设定或明确选新环境 |
| 启动连不上库 | 私有服务、端口、URL及账号 | 修复连接后重启，不输出口令 |
| Flyway拒绝接管 | 表结构与V004基线是否一致 | 备份、比较差异、制定迁移，不强行清库 |
| 登入一直401 | 账号、既有哈希、启用状态 | 演示初始口令与现有账号不一定相同 |
| 写入403 | CSRF是否更新、角色 | 重新取token，确认会话，不能直接关闭CSRF |
| 库存409 | 可用、预留、版本及原请求键 | 刷新并依业务码处理 |
| 对账不一致 | 关联订单、历史流水、单据 | 先保留证据，定位缺失，不直接覆写余额 |
| 到期订单仍预留 | 任务启用、当前时间、批次失败指标及日志 | 确认扫描延迟与失败原因 |
| 浏览器控制不可用 | Work／Edge连接权限 | 与应用HTTP健康分开记录，不当作功能已验收 |
