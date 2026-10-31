#Requires -Version 7.0
param([ValidateRange(1024,65535)][int]$Port=8083,[ValidateRange(1,600)][int]$TimeoutSeconds=180)
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskDocker=Get-Command docker -ErrorAction SilentlyContinue
if(-not $taskDocker){throw '未找到 Docker。请安装并启动 Docker Engine 或 Docker Desktop（Linux 容器），确认 docker 在 PATH 中。'}
& $taskDocker info --format '{{.ServerVersion}}' | Out-Null
if($LASTEXITCODE -ne 0){throw 'Docker 服务未就绪，请先启动 Docker，确认当前使用 Linux 容器。'}
& $taskDocker compose version | Out-Null
if($LASTEXITCODE -ne 0){throw '需要 Docker Compose V2，请确认 docker compose version 可以执行。'}
$taskLocal=Join-Path $taskRoot '.local'
[IO.Directory]::CreateDirectory($taskLocal) | Out-Null
$taskEnv=Join-Path $taskLocal 'compose.env'
if(-not(Test-Path -LiteralPath $taskEnv)){
    $taskValues=@("STOCKFLOW_HTTP_PORT=$Port")
    foreach($taskKey in @('STOCKFLOW_MYSQL_ROOT_PASSWORD','STOCKFLOW_DB_PASSWORD','STOCKFLOW_DEMO_PASSWORD')){
        $taskValues+=$taskKey+'='+[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    }
    [IO.File]::WriteAllLines($taskEnv,$taskValues,[Text.UTF8Encoding]::new($false))
}
$taskConfig=ConvertFrom-StringData ([IO.File]::ReadAllText($taskEnv))
$taskSavedPort=0
if(-not [int]::TryParse($taskConfig.STOCKFLOW_HTTP_PORT,[ref]$taskSavedPort) -or $taskSavedPort -lt 1024 -or $taskSavedPort -gt 65535){throw '本机 .local/compose.env 中 STOCKFLOW_HTTP_PORT 无效，请检查配置；脚本未覆盖原文件。'}
if($PSBoundParameters.ContainsKey('Port') -and $Port -ne $taskSavedPort){throw "已保存端口为 $taskSavedPort，与 -Port $Port 不同。请复用原端口，或自行确认并修改 .local/compose.env；脚本未覆盖原配置。"}
foreach($taskKey in @('STOCKFLOW_MYSQL_ROOT_PASSWORD','STOCKFLOW_DB_PASSWORD','STOCKFLOW_DEMO_PASSWORD')){
    if([string]::IsNullOrWhiteSpace($taskConfig[$taskKey])){throw "本机配置缺少 $taskKey，请检查 .local/compose.env；脚本未生成替代口令。"}
}
# Compose 的进程环境优先于文件，探针必须使用真正生效的端口。
$taskEffectivePort=$taskSavedPort
if($env:STOCKFLOW_HTTP_PORT){
    if(-not [int]::TryParse($env:STOCKFLOW_HTTP_PORT,[ref]$taskEffectivePort) -or $taskEffectivePort -lt 1024 -or $taskEffectivePort -gt 65535){throw '环境变量 STOCKFLOW_HTTP_PORT 无效。'}
    if($PSBoundParameters.ContainsKey('Port') -and $Port -ne $taskEffectivePort){throw '-Port 与环境变量 STOCKFLOW_HTTP_PORT 不一致，请只使用一致的配置。'}
}
& $taskDocker compose --project-directory $taskRoot --env-file $taskEnv config --quiet
if($LASTEXITCODE -ne 0){throw 'Compose 配置检查失败，未启动容器。'}
& $taskDocker compose --project-directory $taskRoot --env-file $taskEnv up --build -d
if($LASTEXITCODE -ne 0){throw '容器启动失败，请检查上方 Docker 错误；现有数据库未删除。'}
$taskUrl="http://127.0.0.1:$taskEffectivePort"
$taskDeadline=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
Write-Host "正在等待应用与数据库就绪：$taskUrl"
do{
    try{
        $taskReadiness=Invoke-RestMethod -Uri "$taskUrl/api/health/readiness" -TimeoutSec 5
        if($taskReadiness.status -eq 'UP'){
            Write-Host "StockFlow 已就绪：$taskUrl"
            Write-Host '演示账号：warehouse / buyer / buyer2 / admin。初始口令见 .local/compose.env 的 STOCKFLOW_DEMO_PASSWORD；若用环境变量覆盖，则以该值为准。'
            Write-Host '已有账号的密码不会因重启改变。停止服务可执行 docker compose --env-file .local/compose.env down，不要附加 -v。'
            return
        }
    }catch{# 启动期间的连接失败与503只用于继续等待，超时后统一报错。
    }
    if([DateTime]::UtcNow -lt $taskDeadline){Start-Sleep -Seconds 2}
}while([DateTime]::UtcNow -lt $taskDeadline)
throw "等待 $TimeoutSeconds 秒后应用仍未就绪。请运行 docker compose --env-file .local/compose.env logs --tail=80 app 检查；未报告启动成功，也未删除容器或数据库。"
