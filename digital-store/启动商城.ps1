param([int]$Port=8094)
$ErrorActionPreference='Stop'
$taskNode=(Get-Command node -ErrorAction SilentlyContinue).Source
if(-not $taskNode){$taskNode=Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'}
if(-not(Test-Path -LiteralPath $taskNode)){throw '需要安装Node.js 24。'}
$env:PORT=[string]$Port
Push-Location $PSScriptRoot
try{& $taskNode '--env-file-if-exists=.env' 'server/start.mjs'}finally{Pop-Location}
