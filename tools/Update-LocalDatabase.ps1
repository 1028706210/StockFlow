param([string]$MySqlHome="$env:ProgramFiles\MySQL\MySQL Server 8.0")
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$taskClient=Join-Path $MySqlHome 'bin\mysql.exe'
$taskConfig=Join-Path $taskRoot '.local\mysql-admin.cnf'
if(-not(Test-Path -LiteralPath $taskClient)){throw 'MySQL client is required.'}
if(-not(Test-Path -LiteralPath $taskConfig)){throw 'Run Start-LocalDatabase.ps1 before upgrading the private local databases.'}
$taskSchema=[IO.File]::ReadAllText((Join-Path $taskRoot 'src\main\resources\schema.sql'))
$taskUpgrade=[IO.File]::ReadAllText((Join-Path $taskRoot 'db\migrations\V002__accounts_and_fulfillment.sql'))
foreach($taskDatabase in @('stockflow_dev','stockflow_test')){
    $taskActual=& $taskClient "--defaults-extra-file=$taskConfig" "--database=$taskDatabase" --batch --skip-column-names -e 'SELECT DATABASE()'
    if($LASTEXITCODE -ne 0 -or "$taskActual" -ne $taskDatabase){throw 'Refuse schema upgrade outside the isolated StockFlow database.'}
    & $taskClient "--defaults-extra-file=$taskConfig" "--database=$taskDatabase" --default-character-set=utf8mb4 --batch -e ($taskSchema+"`n"+$taskUpgrade) | Out-Null
    if($LASTEXITCODE -ne 0){throw "Schema upgrade failed for $taskDatabase"}
    "StockFlow schema upgraded: $taskDatabase (existing business data retained)."
}
