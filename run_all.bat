@echo off
cd /d "%~dp0"
chcp 65001 > nul
title KHOI DONG HE THONG SON TINH THUY TINH 2V2
echo =======================================================
echo     KHOI DONG TOAN BO HE THONG GAME MANG 2V2...
echo =======================================================
start "Master Server (Port 8888)" cmd /c run_master.bat
timeout /t 2 /nobreak > nul
start "Game Server (TCP 9999, UDP 9998)" cmd /c run_gameserver.bat
timeout /t 2 /nobreak > nul
start "Game Client 1" cmd /c run_client.bat
echo He thong da khoi dong xong!
