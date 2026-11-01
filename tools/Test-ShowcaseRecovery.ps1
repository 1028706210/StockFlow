#Requires -Version 7
param([string]$MySqlHome="$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskLocal=Join-Path $taskRoot '.local'
$taskState=Get-Content -LiteralPath (Join-Path $taskLocal 'showcase.json') -Raw|ConvertFrom-Json
if($taskState.database -ne 'stockflow_showcase'){throw '仅允许备份独立展示库。'}
$taskMysql=Join-Path $MySqlHome 'bin\mysql.exe'
$taskDump=Join-Path $MySqlHome 'bin\mysqldump.exe'
$taskConfig=Join-Path $taskLocal 'mysql-admin.cnf'
$taskFolder=Join-Path $taskLocal 'backups'
[IO.Directory]::CreateDirectory($taskFolder)|Out-Null
$taskStamp=Get-Date -Format 'yyyyMMddHHmmssfff'
$taskRestore='stockflow_restore_'+$taskStamp
if($taskRestore -notmatch '^stockflow_restore_\d{17}$'){throw '恢复库名称校验失败。'}
$taskFile=Join-Path $taskFolder ($taskStamp+'.sql')
$taskTimer=[Diagnostics.Stopwatch]::StartNew()
function Read-Scalar([string]$Query){
    $taskResult=& $taskMysql "--defaults-extra-file=$taskConfig" --default-character-set=utf8mb4 --batch --skip-column-names -e $Query
    if($LASTEXITCODE -ne 0){throw '数据库只读校验失败。'}
    return $taskResult
}
$taskTables=@(Read-Scalar 'SHOW TABLES FROM stockflow_showcase')
foreach($taskTable in $taskTables){if($taskTable -notmatch '^[a-z_]+$'){throw '表名校验失败。'}}
$taskCounts=@{}
foreach($taskTable in $taskTables){$taskCounts[$taskTable]=[long](Read-Scalar "SELECT COUNT(*) FROM stockflow_showcase.$taskTable")}
& $taskDump "--defaults-extra-file=$taskConfig" --default-character-set=utf8mb4 --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF --skip-add-drop-table "--result-file=$taskFile" stockflow_showcase
if($LASTEXITCODE -ne 0){throw '备份失败，不能作为恢复点。'}
# CREATE without IF NOT EXISTS: an existing database must never be overwritten.
$null=Read-Scalar "CREATE DATABASE $taskRestore CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs"
Get-Content -LiteralPath $taskFile -Raw|& $taskMysql "--defaults-extra-file=$taskConfig" --default-character-set=utf8mb4 $taskRestore
if($LASTEXITCODE -ne 0){throw '新建隔离库恢复失败，保留文件和恢复库供核对。'}
foreach($taskTable in $taskTables){
    $taskActual=[long](Read-Scalar "SELECT COUNT(*) FROM $taskRestore.$taskTable")
    if($taskActual -ne $taskCounts[$taskTable]){throw "恢复表数量不一致：$taskTable"}
}
$taskInvalid=[long](Read-Scalar "SELECT COUNT(*) FROM $taskRestore.inventory WHERE on_hand<reserved OR reserved<0")
$taskBalance=[long](Read-Scalar "SELECT COUNT(*) FROM $taskRestore.inventory i LEFT JOIN (SELECT sku_id,SUM(CASE WHEN operation_type IN ('INBOUND','ADJUST_IN','RETURN_IN') THEN quantity WHEN operation_type IN ('SHIP','ADJUST_OUT') THEN -quantity ELSE 0 END) h,SUM(CASE WHEN operation_type='RESERVE' THEN quantity WHEN operation_type IN ('RELEASE','SHIP') THEN -quantity ELSE 0 END) r FROM $taskRestore.inventory_ledger GROUP BY sku_id) l ON l.sku_id=i.sku_id WHERE i.on_hand<>COALESCE(l.h,0) OR i.reserved<>COALESCE(l.r,0)")
$taskOrders=[long](Read-Scalar "SELECT COUNT(*) FROM $taskRestore.inventory i LEFT JOIN (SELECT oi.sku_id,SUM(oi.quantity) r FROM $taskRestore.order_item oi JOIN $taskRestore.customer_order o ON o.id=oi.order_id WHERE o.status IN ('PENDING_CONFIRM','CONFIRMED') GROUP BY oi.sku_id) x ON x.sku_id=i.sku_id WHERE i.reserved<>COALESCE(x.r,0)")
if($taskInvalid -ne 0 -or $taskBalance -ne 0 -or $taskOrders -ne 0){throw '恢复库库存不变量或对账校验未通过。'}
$taskVersion=Read-Scalar "SELECT MAX(CAST(version AS UNSIGNED)) FROM $taskRestore.flyway_schema_history WHERE success=1"
$taskTimer.Stop()
$taskReport=[ordered]@{date=(Get-Date -Format 'yyyy-MM-dd');source='stockflow_showcase';restore=$taskRestore;tables=$taskTables.Count;counts=$taskCounts;schemaVersion=[int]$taskVersion;inventoryViolations=$taskInvalid;ledgerMismatches=$taskBalance;orderReservationMismatches=$taskOrders;elapsedMilliseconds=$taskTimer.ElapsedMilliseconds;backupSha256=(Get-FileHash -LiteralPath $taskFile -Algorithm SHA256).Hash;notice='只恢复到本次新建的隔离库；未覆盖或删除源库；不代表生产环境RPO/RTO。'}
$taskJson=$taskReport|ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $taskFolder ($taskStamp+'-report.json')),$taskJson,[Text.UTF8Encoding]::new($false))
$taskJson
