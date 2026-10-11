@echo off
title WA Marketing Backend Server
echo ==============================================================================
echo                WA MARKETING - FASTAPI BACKEND SERVER
echo ==============================================================================
echo.
cd /d "%~dp0backend"
echo Starting backend server on 0.0.0.0:8005...
echo API Health: http://localhost:8005/health
echo Swagger Docs: http://localhost:8005/docs
echo.
.venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8005 --reload
pause
