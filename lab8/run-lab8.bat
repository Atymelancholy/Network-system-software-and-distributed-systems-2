@echo off
setlocal
set "ROOT=%~dp0"
set "MPJ_HOME=%ROOT%lib\mpj-v0_44"
set "PATH=%MPJ_HOME%\bin;%PATH%"

set "GROUPS=%~1"
if "%GROUPS%"=="" set "GROUPS=2"
set "N=%~2"
if "%N%"=="" set "N=1024"
set "MODE=%~3"
if "%MODE%"=="" set "MODE=nonblocking"
set "NP=%~4"
if "%NP%"=="" set "NP=4"
set "STRIPS=%~5"
if "%STRIPS%"=="" set "STRIPS=8"
set "DATADIR=%~6"
if "%DATADIR%"=="" set "DATADIR=data"

echo MPJ_HOME=%MPJ_HOME%
echo groups=%GROUPS% n=%N% mode=%MODE% np=%NP% strips=%STRIPS% datadir=%DATADIR%

chcp 65001 >nul
call mvn -q -f "%ROOT%pom.xml" compile
if errorlevel 1 exit /b 1

java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" "-Dstderr.encoding=UTF-8" -jar "%MPJ_HOME%\lib\starter.jar" -np %NP% -cp "%ROOT%target\classes" org.example.lab8.Lab8App %GROUPS% %N% %MODE% %STRIPS% %DATADIR%
