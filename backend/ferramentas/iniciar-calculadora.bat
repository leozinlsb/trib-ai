@echo off
rem Sobe a Calculadora RTC oficial em http://localhost:8080/api (perfil offline), sem WSL nem Docker.
rem Uso: iniciar-calculadora.bat [pasta]   (padrao: %USERPROFILE%\Desktop\calculadora-rtc)
rem A pasta e preparada/atualizada por atualizar_calculadora.py. Para parar: Ctrl+C nesta janela.

set "PASTA=%~1"
if "%PASTA%"=="" set "PASTA=%USERPROFILE%\Desktop\calculadora-rtc"

if not exist "%PASTA%\api-regime-geral.jar" (
  echo Nao encontrei "%PASTA%\api-regime-geral.jar". Rode antes: python -I ferramentas\atualizar_calculadora.py
  exit /b 1
)
if not exist "%PASTA%\calculadora\db\calculadora-pro.db" (
  echo Nao encontrei "%PASTA%\calculadora\db\calculadora-pro.db". Rode antes: python -I ferramentas\atualizar_calculadora.py
  exit /b 1
)

cd /d "%PASTA%"
java -jar api-regime-geral.jar --spring.profiles.active=offline
