@echo off
cd /d "%~dp0"
chcp 65001 > nul
title MASTER SERVER - SON TINH THUY TINH (Port 8888)
echo =======================================================
echo     DANG KHOI CHAY MASTER SERVER (PORT 8888)...
echo =======================================================
java -cp bin master.MasterServer
pause
