param([ValidateSet('test','package','run','stop-db')][string]$Action='test',[string]$Maven)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskState=Get-Content -LiteralPath (Join-Path $taskRoot '.local\database.json') -Raw | ConvertFrom-Json
$env:STOCKFLOW_DB_URL="jdbc:mysql://127.0.0.1:$($taskState.port)/stockflow_dev?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC"
$env:STOCKFLOW_DB_USERNAME='stockflow'
$env:STOCKFLOW_DB_PASSWORD=$taskState.devPassword
$env:STOCKFLOW_TEST_DB_URL="jdbc:mysql://127.0.0.1:$($taskState.port)/stockflow_test?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC"
$env:STOCKFLOW_TEST_DB_USERNAME='stockflow_test'
$env:STOCKFLOW_TEST_DB_PASSWORD=$taskState.testPassword
$env:STOCKFLOW_DEMO_PASSWORD=$taskState.demoPassword
if($Action -eq 'stop-db'){
    $taskPid=Join-Path $taskRoot '.local\mysql.pid'
    if(-not (Test-Path -LiteralPath $taskPid)){throw 'No StockFlow MySQL PID file.'}
    $taskPidNumber=[int](Get-Content -LiteralPath $taskPid)
    $taskRunning=Get-CimInstance Win32_Process -Filter "ProcessId=$taskPidNumber"
    if($taskRunning.Name -ne 'mysqld.exe' -or -not $taskRunning.CommandLine.Contains((Join-Path $taskRoot '.local\mysql'))){throw 'Process does not belong to StockFlow.'}
    Stop-Process -Id $taskPidNumber
    'Stopped StockFlow private MySQL.';exit 0
}
if(-not $Maven){
    $taskCommand=Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if($taskCommand){$Maven=$taskCommand.Source}
    elseif($env:MAVEN_HOME -and (Test-Path -LiteralPath (Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'))){$Maven=Join-Path $env:MAVEN_HOME 'bin\mvn.cmd'}
    else{throw 'Provide -Maven with your mvn.cmd path, or configure Maven on PATH.'}
}
Push-Location $taskRoot
try{
    if($Action -eq 'run'){
        & java -jar (Join-Path $taskRoot 'target\stockflow-0.1.0-SNAPSHOT.jar')
    }else{
        & $Maven -B '-Dstyle.color=never' $Action
    }
    if($LASTEXITCODE -ne 0){throw "StockFlow $Action failed with exit code $LASTEXITCODE"}
}finally{Pop-Location}
