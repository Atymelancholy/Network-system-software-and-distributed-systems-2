@echo off
setlocal
set "ROOT=%~dp0"
set "MPJ_HOME=%ROOT%lib\mpj-v0_44"
set "PATH=%MPJ_HOME%\bin;%PATH%"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=blocking"
set "N=%~2"
if "%N%"=="" set "N=3072"
set "NP=%~3"
if "%NP%"=="" set "NP=4"
set "STRIPS=%~4"
if "%STRIPS%"=="" set "STRIPS=8"

echo MPJ_HOME=%MPJ_HOME%
echo mode=%MODE% n=%N% np=%NP% strips=%STRIPS%

chcp 65001 >nul
call mvn -q -f "%ROOT%pom.xml" compile
if errorlevel 1 exit /b 1

java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -jar "%MPJ_HOME%\lib\starter.jar" -np %NP% -cp "%ROOT%target\classes" org.example.lab7.Lab7App %MODE% %N% %STRIPS%
