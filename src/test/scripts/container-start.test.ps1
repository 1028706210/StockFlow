#Requires -Version 7.0
# 验证启动脚本的边界，不创建容器或接触开发数据库；真实容器在CI另行验收。
$ErrorActionPreference='Stop'
$testRepo=Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
$testSource=Join-Path $testRepo 'tools/Start-Container.ps1'
$testRoot=Join-Path ([IO.Path]::GetTempPath()) ('stockflow-start-'+[Guid]::NewGuid().ToString('N'))
$savedPort=$env:STOCKFLOW_HTTP_PORT
$global:stockflowFixtureMode='ready'
$global:stockflowFixtureCalls=[Collections.Generic.List[string]]::new()
$global:stockflowFixtureUri=''

function Get-Command {
    param([string]$Name,[string]$ErrorAction)
    if($Name -eq 'docker' -and $global:stockflowFixtureMode -eq 'missing'){return $null}
    Microsoft.PowerShell.Core\Get-Command -Name $Name -ErrorAction Stop
}
function docker {
    $call=$args -join ' '
    $global:stockflowFixtureCalls.Add($call)
    $global:LASTEXITCODE=0
    if($global:stockflowFixtureMode -eq 'daemon' -and $args[0] -eq 'info'){$global:LASTEXITCODE=1}
    if($global:stockflowFixtureMode -eq 'build' -and $call -match ' up --build -d$'){$global:LASTEXITCODE=1}
}
function Invoke-RestMethod {
    param([string]$Uri,[int]$TimeoutSec)
    $global:stockflowFixtureUri=$Uri
    if($global:stockflowFixtureMode -eq 'pending'){return @{status='DOWN'}}
    return @{status='UP'}
}
function Assert-True([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
function New-Fixture([string]$Name){
    $dir=Join-Path $testRoot $Name
    $null=New-Item -ItemType Directory -Path (Join-Path $dir 'tools') -Force
    Copy-Item -LiteralPath $testSource -Destination (Join-Path $dir 'tools/Start-Container.ps1')
    return $dir
}
function Expect-Failure([scriptblock]$Action,[string]$Pattern){
    try{& $Action}catch{if($_.Exception.Message -notmatch $Pattern){throw};return}
    throw "预期失败但实际成功：$Pattern"
}
try{
    Remove-Item Env:STOCKFLOW_HTTP_PORT -ErrorAction SilentlyContinue
    $fresh=New-Fixture 'fresh'
    & (Join-Path $fresh 'tools/Start-Container.ps1') -Port 8090
    $envPath=Join-Path $fresh '.local/compose.env'
    $original=[IO.File]::ReadAllText($envPath)
    $config=ConvertFrom-StringData $original
    Assert-True ($config.STOCKFLOW_DEMO_PASSWORD -match '^[0-9A-F]{48}$') '未生成48位随机口令'
    Assert-True ($global:stockflowFixtureUri -eq 'http://127.0.0.1:8090/api/health/readiness') '探针未使用实际端口'
    & (Join-Path $fresh 'tools/Start-Container.ps1')
    Assert-True ([IO.File]::ReadAllText($envPath) -eq $original) '重启覆盖了原口令或端口'
    Write-Host 'PASS 新环境生成私有配置、检查readiness，重启保留口令与端口'

    Expect-Failure { & (Join-Path $fresh 'tools/Start-Container.ps1') -Port 8091 } '已保存端口'
    Assert-True ([IO.File]::ReadAllText($envPath) -eq $original) '端口冲突修改了配置'
    Write-Host 'PASS 显式端口冲突拒绝且不覆盖原配置'

    $env:STOCKFLOW_HTTP_PORT='8092'
    & (Join-Path $fresh 'tools/Start-Container.ps1')
    Assert-True ($global:stockflowFixtureUri -eq 'http://127.0.0.1:8092/api/health/readiness') '未遵守Compose环境优先级'
    $env:STOCKFLOW_HTTP_PORT='invalid'
    Expect-Failure { & (Join-Path $fresh 'tools/Start-Container.ps1') } '环境变量.*无效'
    Remove-Item Env:STOCKFLOW_HTTP_PORT
    Write-Host 'PASS 环境端口覆盖与非法值检查'

    foreach($mode in @('missing','daemon','build','pending')){
        $global:stockflowFixtureMode=$mode
        $dir=New-Fixture $mode
        $pattern=switch($mode){'missing'{'未找到 Docker'}'daemon'{'Docker 服务未就绪'}'build'{'容器启动失败'}'pending'{'仍未就绪'}}
        Expect-Failure { & (Join-Path $dir 'tools/Start-Container.ps1') -TimeoutSeconds 1 } $pattern
        if($mode -in @('missing','daemon')){Assert-True (-not (Test-Path (Join-Path $dir '.local/compose.env'))) 'Docker未就绪却生成了配置'}
        Write-Host "PASS $mode 不会报告启动成功"
    }
    Assert-True (-not ($global:stockflowFixtureCalls | Where-Object {$_ -match '(^| )(down|rm|volume)( |$)'})) '脚本执行了数据删除命令'
    Write-Host '共7组启动边界检查通过；Docker与HTTP使用受控替代，不等同容器实测。'
}finally{
    if($null -eq $savedPort){Remove-Item Env:STOCKFLOW_HTTP_PORT -ErrorAction SilentlyContinue}else{$env:STOCKFLOW_HTTP_PORT=$savedPort}
    # 只清理本测试新建的随机临时目录，先校验其父目录和专用前缀。
    $resolved=[IO.Path]::GetFullPath($testRoot)
    if([IO.Path]::GetDirectoryName($resolved).TrimEnd([IO.Path]::DirectorySeparatorChar) -eq [IO.Path]::GetTempPath().TrimEnd([IO.Path]::DirectorySeparatorChar) -and [IO.Path]::GetFileName($resolved).StartsWith('stockflow-start-')){
        Remove-Item -LiteralPath $resolved -Recurse -Force -ErrorAction SilentlyContinue
    }
    Remove-Variable stockflowFixtureMode,stockflowFixtureCalls,stockflowFixtureUri -Scope Global -ErrorAction SilentlyContinue
}
