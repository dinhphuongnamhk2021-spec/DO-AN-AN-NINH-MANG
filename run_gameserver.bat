@echo off
cd /d "%~dp0"
chcp 65001 > nul
title GAME SERVER - SON TINH THUY TINH (TCP: 9999, UDP: 9998)
echo =======================================================
echo     DANG KHOI CHAY GAME SERVER (TCP: 9999, UDP: 9998)...
echo =======================================================
java -cp bin gameserver.GameServerMain
pause
