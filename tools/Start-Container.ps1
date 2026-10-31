param([int]$Port=8083)
$ErrorActionPreference='Stop'
if($Port -lt 1024 -or $Port -gt 65535){throw 'Use a local port from 1024 to 65535.'}
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskDocker=Get-Command docker -ErrorAction SilentlyContinue
if(-not $taskDocker){throw 'Start Docker Desktop with Linux containers and expose docker on PATH.'}
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
& $taskDocker.Source compose --project-directory $taskRoot --env-file $taskEnv up --build -d
if($LASTEXITCODE -ne 0){throw 'Container startup failed.'}
Write-Host 'Containers started. Local URL and generated demo password are stored in .local/compose.env.'
