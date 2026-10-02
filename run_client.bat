@echo off
cd /d "%~dp0"
chcp 65001 > nul
title CLIENT - SON TINH THUY TINH
echo =======================================================
echo     DANG KHOI CHAY GAME CLIENT 2D...
echo =======================================================
java -cp bin client.MainGameFrame
pause
