# FIND-MISSING-PEP - Unified PowerShell Launcher
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host "          FIND-MISSING-PEP - Launching All Services                  " -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host ""

if (Get-Command py -ErrorAction SilentlyContinue) {
    & py run_all.py @args
} elseif (Get-Command python -ErrorAction SilentlyContinue) {
    & python run_all.py @args
} else {
    Write-Host "[ERROR] Python (py or python) is not found in PATH." -ForegroundColor Red
    exit 1
}
