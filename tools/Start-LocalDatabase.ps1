param([int]$Port = 3308, [string]$MySqlHome = "$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
$taskLocal = Join-Path $taskRoot '.local'
$taskData = Join-Path $taskLocal 'mysql'
$taskStateFile = Join-Path $taskLocal 'database.json'
$taskServer = Join-Path $MySqlHome 'bin\mysqld.exe'
$taskClient = Join-Path $MySqlHome 'bin\mysql.exe'
if (-not (Test-Path -LiteralPath $taskServer)) { throw 'MySQL Server 8 is required; provide -MySqlHome.' }
[IO.Directory]::CreateDirectory($taskLocal) | Out-Null
function New-LocalPassword { return [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)) }
if (Test-Path -LiteralPath $taskStateFile) {
    $taskState = Get-Content -LiteralPath $taskStateFile -Raw | ConvertFrom-Json
    if ($taskState.port -ne $Port) { throw 'Existing local instance uses a different port.' }
} else {
    if (Test-Path -LiteralPath (Join-Path $taskData 'mysql')) { throw 'Existing data directory has no matching credential record; refusing to reinitialize.' }
    $taskState = [pscustomobject]@{port=$Port;adminPassword=(New-LocalPassword);devPassword=(New-LocalPassword);testPassword=(New-LocalPassword);demoPassword=(New-LocalPassword)}
    [IO.File]::WriteAllText($taskStateFile,($taskState | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
$taskClientConfig = Join-Path $taskLocal 'mysql-admin.cnf'
[IO.File]::WriteAllText($taskClientConfig,("[client]" + [Environment]::NewLine + "user=root" + [Environment]::NewLine + "password=" + $taskState.adminPassword + [Environment]::NewLine + "host=127.0.0.1" + [Environment]::NewLine + "port=" + $Port),[Text.UTF8Encoding]::new($false))
$taskExisting = New-Object Net.Sockets.TcpClient
try { $taskExisting.Connect('127.0.0.1',$Port); $taskPortOpen=$true } catch { $taskPortOpen=$false } finally { $taskExisting.Dispose() }
if ($taskPortOpen) {
    & $taskClient "--defaults-extra-file=$taskClientConfig" --connect-timeout=2 --batch --skip-column-names -e 'SELECT 1' 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Port $Port is occupied by a different service; no changes made." }
    "StockFlow MySQL already running on 127.0.0.1:$Port"; exit 0
}
$taskFresh = -not (Test-Path -LiteralPath (Join-Path $taskData 'mysql'))
[IO.Directory]::CreateDirectory($taskData) | Out-Null
$taskLog = Join-Path $taskLocal 'mysql-error.log'
if ($taskFresh) {
    $taskInit = Start-Process -FilePath $taskServer -ArgumentList @('--no-defaults','--initialize-insecure',('--basedir="'+$MySqlHome+'"'),('--datadir="'+$taskData+'"'),('--log-error="'+$taskLog+'"')) -WindowStyle Hidden -Wait -PassThru
    if ($taskInit.ExitCode -ne 0) { throw "Private MySQL initialization failed; inspect $taskLog locally." }
}
$taskArguments = @('--no-defaults',('--basedir="'+$MySqlHome+'"'),('--datadir="'+$taskData+'"'),"--port=$Port",'--bind-address=127.0.0.1','--mysqlx=OFF',('--log-error="'+$taskLog+'"'),('--pid-file="'+(Join-Path $taskLocal 'mysql.pid')+'"'))
$taskBootstrap = Join-Path $taskLocal 'mysql-bootstrap.sql'
if ($taskFresh) {
    $taskSql = @"
ALTER USER 'root'@'localhost' IDENTIFIED BY '$($taskState.adminPassword)';
CREATE DATABASE stockflow_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE DATABASE stockflow_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs;
CREATE USER 'stockflow'@'localhost' IDENTIFIED BY '$($taskState.devPassword)';
CREATE USER 'stockflow_test'@'localhost' IDENTIFIED BY '$($taskState.testPassword)';
GRANT ALL ON stockflow_dev.* TO 'stockflow'@'localhost';
GRANT ALL ON stockflow_test.* TO 'stockflow_test'@'localhost';
"@
    [IO.File]::WriteAllText($taskBootstrap,$taskSql,[Text.UTF8Encoding]::new($false))
    $taskArguments += '--init-file="' + $taskBootstrap + '"'
}
$taskProcess = Start-Process -FilePath $taskServer -ArgumentList $taskArguments -WindowStyle Hidden -PassThru
$taskReady=$false
for($taskAttempt=0;$taskAttempt -lt 120;$taskAttempt++){
    Start-Sleep -Milliseconds 500
    if($taskProcess.HasExited){throw "Private MySQL exited; inspect $taskLog locally."}
    & $taskClient "--defaults-extra-file=$taskClientConfig" --connect-timeout=1 --batch --skip-column-names -e 'SELECT 1' 2>$null | Out-Null
    if($LASTEXITCODE -eq 0){$taskReady=$true;break}
}
if(-not $taskReady){throw "Private MySQL did not become ready; inspect $taskLog locally."}
if(Test-Path -LiteralPath $taskBootstrap){Remove-Item -LiteralPath $taskBootstrap}
[IO.File]::WriteAllText((Join-Path $taskLocal 'demo-login.txt'),("Users: buyer, buyer2, warehouse, admin" + [Environment]::NewLine + "Local demo password: " + $taskState.demoPassword),[Text.UTF8Encoding]::new($false))
"StockFlow MySQL ready on 127.0.0.1:$Port; dev/test schemas isolated; credentials in ignored .local directory."
