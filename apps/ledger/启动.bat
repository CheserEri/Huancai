@echo off
chcp 65001 >nul
cd /d %~dp0
title 幻彩课时记账系统

echo.
echo   正在启动...
echo.

python app.py
if errorlevel 1 (
  echo.
  echo   [!] 启动失败，可能没有安装 Python
  echo       请到 https://www.python.org/downloads/ 下载安装
  echo       安装时务必勾选 "Add Python to PATH"
  echo.
  pause
)
