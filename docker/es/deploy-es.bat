@echo off
REM Degel ES 集群一键部署（Windows / Docker Desktop）
REM 用空 DOCKER_CONFIG 绕过 credsStore（SSH 会话无交互登录会话，凭据助手不可用）
mkdir C:\Users\Admin\degel-es\dc 2>nul
echo {} > C:\Users\Admin\degel-es\dc\config.json
set DOCKER_CONFIG=C:\Users\Admin\degel-es\dc
cd /d C:\Users\Admin\degel-es
docker compose up -d
echo.
echo === exit code: %ERRORLEVEL% ===
