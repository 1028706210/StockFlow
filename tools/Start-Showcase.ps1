#Requires -Version 7
param([switch]$Rebuild,[string]$Maven,[ValidateRange(1024,65535)][int]$HttpPort=8096,[int]$DatabasePort=3308,[string]$MySqlHome="$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskLocal=Join-Path $taskRoot '.local'
$taskFile=Join-Path $taskLocal 'showcase.json'
$taskMysql=Join-Path $MySqlHome 'bin\mysql.exe'
if(-not(Get-Command node -ErrorAction SilentlyContinue)){throw '需要 Node.js 24 才能准备展示商品。'}
if([int]((& node -p 'process.versions.node.split(".")[0]')|Select-Object -Last 1) -lt 24){throw '需要 Node.js 24 或以上版本。'}
& (Join-Path $PSScriptRoot 'Start-LocalDatabase.ps1') -Port $DatabasePort -MySqlHome $MySqlHome
if(Test-Path -LiteralPath $taskFile){
    $taskState=Get-Content -LiteralPath $taskFile -Raw|ConvertFrom-Json
    if($taskState.database -ne 'stockflow_showcase' -or $taskState.httpPort -ne $HttpPort -or $taskState.databasePort -ne $DatabasePort){throw '已有展示配置与本次参数不同，未修改数据库或端口。'}
}else{
    $taskConfig=Join-Path $taskLocal 'mysql-admin.cnf'
    $taskCount=(& $taskMysql "--defaults-extra-file=$taskConfig" --batch --skip-column-names -e "SELECT (SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='stockflow_showcase')+(SELECT COUNT(*) FROM mysql.user WHERE user='stockflow_showcase')")
    if($LASTEXITCODE -ne 0 -or [int]$taskCount -ne 0){throw '发现已有展示库或账号但没有对应私有配置，拒绝覆盖。'}
    $taskState=[pscustomobject]@{database='stockflow_showcase';databasePort=$DatabasePort;httpPort=$HttpPort;password=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24));demoPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))}
    $taskSql="CREATE DATABASE stockflow_showcase CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs; CREATE USER 'stockflow_showcase'@'localhost' IDENTIFIED BY '$($taskState.password)'; GRANT ALL ON stockflow_showcase.* TO 'stockflow_showcase'@'localhost';"
    $taskSql|& $taskMysql "--defaults-extra-file=$taskConfig" --default-character-set=utf8mb4
    if($LASTEXITCODE -ne 0){throw '展示库创建失败；未覆盖现有配置，请核对本机 MySQL。'}
    [IO.File]::WriteAllText($taskFile,($taskState|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
}
$taskBase="http://127.0.0.1:$HttpPort"
$taskOpen=$false
try{$taskSocket=[Net.Sockets.TcpClient]::new();$taskSocket.Connect('127.0.0.1',$HttpPort);$taskOpen=$true}catch{}finally{if($taskSocket){$taskSocket.Dispose()}}
$taskEnv=@{
    STOCKFLOW_DB_URL="jdbc:mysql://127.0.0.1:$DatabasePort/stockflow_showcase?allowPublicKeyRetrieval=true&useSSL=false&connectionTimeZone=UTC";
    STOCKFLOW_DB_USERNAME='stockflow_showcase';STOCKFLOW_DB_PASSWORD=$taskState.password;STOCKFLOW_DEMO_PASSWORD=$taskState.demoPassword;
    STOCKFLOW_SHOWCASE_ENABLED='true';SERVER_PORT=[string]$HttpPort;SERVER_ADDRESS='127.0.0.1';STOCKFLOW_SHOWCASE_URL=$taskBase
}
$taskPrevious=@{}
try{
    foreach($taskPair in $taskEnv.GetEnumerator()){$taskPrevious[$taskPair.Key]=[Environment]::GetEnvironmentVariable($taskPair.Key);[Environment]::SetEnvironmentVariable($taskPair.Key,$taskPair.Value)}
    $taskJar=Join-Path $taskRoot 'target\stockflow-0.1.0-SNAPSHOT.jar'
    if(-not $taskOpen){
        if($Rebuild -or -not(Test-Path -LiteralPath $taskJar)){
            if(-not $Maven){$taskMaven=Get-Command mvn.cmd -ErrorAction SilentlyContinue;if($taskMaven){$Maven=$taskMaven.Source}else{throw '请通过 -Maven 指定 mvn.cmd，或先打包项目。'}}
            Push-Location $taskRoot
            try{& $Maven -B '-Dstyle.color=never' '-DskipTests' package;if($LASTEXITCODE -ne 0){throw '项目打包失败。'}}finally{Pop-Location}
        }
        $taskJava=Get-Command java -ErrorAction Stop
        $taskProcess=Start-Process -FilePath $taskJava.Source -ArgumentList @('-jar',('"'+$taskJar+'"')) -WorkingDirectory $taskRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $taskLocal 'showcase-java.log') -RedirectStandardError (Join-Path $taskLocal 'showcase-java-error.log') -PassThru
        [IO.File]::WriteAllText((Join-Path $taskLocal 'showcase.pid'),[string]$taskProcess.Id)
    }elseif($Rebuild){throw '展示端口正在使用；请先正常停止该展示服务后再重建，不会自动结束进程。'}
    $taskReady=$false
    for($taskAttempt=0;$taskAttempt -lt 60;$taskAttempt++){
        try{$taskHealth=Invoke-RestMethod "$taskBase/api/health/readiness" -TimeoutSec 2;if($taskHealth.status -eq 'UP'){$taskReady=$true;break}}catch{}
        if($taskProcess -and $taskProcess.HasExited){throw '展示服务退出，请查看 .local/showcase-java-error.log。'}
        Start-Sleep -Milliseconds 500
    }
    if(-not $taskReady){throw '展示服务尚未就绪；未宣称准备成功，请检查本机日志。'}
    & node (Join-Path $PSScriptRoot 'showcase\prepare.mjs')
    if($LASTEXITCODE -ne 0){throw '展示数据准备未通过。服务端必须确认使用独立展示库；不会重置或清空任何业务库。'}
    Write-Host "买家前台：$taskBase/shop#products"
    Write-Host "仓管后台：$taskBase/"
    Write-Host '账号 buyer / buyer2 / warehouse / admin；口令仅保存在 .local/showcase.json 的 demoPassword。'
}finally{foreach($taskPair in $taskPrevious.GetEnumerator()){[Environment]::SetEnvironmentVariable($taskPair.Key,$taskPair.Value)}}
