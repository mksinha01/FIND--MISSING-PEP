# FIND-MISSING-PEP — Run Edge CCTV Agent with Production
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Starting FIND-MISSING-PEP Edge CCTV Agent" -ForegroundColor Green
Write-Host "Connected to Production: http://65.20.80.222:8000" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan

$env:PYTHONPATH = "$PSScriptRoot;$env:PYTHONPATH"
py -m edge_agent.main
