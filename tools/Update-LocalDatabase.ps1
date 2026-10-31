param([string]$MySqlHome="$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
if(-not(Test-Path -LiteralPath (Join-Path $taskRoot '.local\database.json'))){throw 'Run Start-LocalDatabase.ps1 first.'}
Write-Host 'Database upgrades are now owned by Flyway at application startup. Start the current JAR to validate and apply versioned migrations; this script does not replay SQL.'
