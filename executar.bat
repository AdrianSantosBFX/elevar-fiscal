@echo off
rem Executa o Elevar Fiscal (requer Java 8 ou superior)
set "DIR=%~dp0"
if exist "%DIR%dist\elevar-fiscal.jar" (
  set "JAR=%DIR%dist\elevar-fiscal.jar"
) else if exist "%DIR%target\elevar-fiscal.jar" (
  set "JAR=%DIR%target\elevar-fiscal.jar"
) else (
  echo Nao encontrei o elevar-fiscal.jar em dist\ nem em target\.
  pause
  exit /b 1
)
java -jar "%JAR%"
if errorlevel 1 pause
