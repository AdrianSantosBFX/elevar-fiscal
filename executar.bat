@echo off
rem Executa o Facilita.sig (requer Java 8 ou superior)
set "DIR=%~dp0"
if exist "%DIR%dist\facilita-sig.jar" (
  set "JAR=%DIR%dist\facilita-sig.jar"
) else if exist "%DIR%target\facilita-sig.jar" (
  set "JAR=%DIR%target\facilita-sig.jar"
) else (
  echo Nao encontrei o facilita-sig.jar em dist\ nem em target\.
  pause
  exit /b 1
)
java -jar "%JAR%"
if errorlevel 1 pause
