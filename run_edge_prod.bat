@echo off
title FIND-MISSING-PEP Edge CCTV Agent (Production)
echo ===================================================
echo Starting FIND-MISSING-PEP Edge CCTV Agent
echo Connected to Production: http://65.20.80.222:8000
echo ===================================================
cd /d "%~dp0"
set PYTHONPATH=%~dp0;%PYTHONPATH%
py -m edge_agent.main
pause
