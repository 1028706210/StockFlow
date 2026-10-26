param([switch]$Rebuild,[string]$Maven,[int]$Port=3308,[string]$MySqlHome="$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference='Stop'
# A private StockFlow instance; course databases are never used.
& (Join-Path $PSScriptRoot 'Start-LocalDatabase.ps1') -Port $Port -MySqlHome $MySqlHome
& (Join-Path $PSScriptRoot 'Update-LocalDatabase.ps1') -MySqlHome $MySqlHome
$taskJar=Join-Path (Split-Path $PSScriptRoot -Parent) 'target\stockflow-0.1.0-SNAPSHOT.jar'
if($Rebuild -or -not(Test-Path -LiteralPath $taskJar)){
    & (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action package -Maven $Maven
}
Write-Host 'StockFlow is starting at http://127.0.0.1:8082. Press Ctrl+C to stop the application.'
& (Join-Path $PSScriptRoot 'Invoke-Local.ps1') -Action run
