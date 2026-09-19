@echo off
setlocal
set "MPJ_HOME=%~dp0lib\mpj-v0_44"
set "PATH=%MPJ_HOME%\bin;%PATH%"

echo Stopping MPJ daemon...
call "%MPJ_HOME%\bin\mpjdaemon.bat" -halt

echo Freeing MPJ ports 40002, 40052, 40055...
for %%P in (40002 40052 40055) do (
    for /f "tokens=5" %%I in ('netstat -ano ^| findstr "%%P"') do (
        if not "%%I"=="0" (
            echo Killing PID %%I on port %%P
            taskkill /F /PID %%I >nul 2>&1
        )
    )
)

echo Done. Start the daemon again:
echo   $env:MPJ_HOME = "C:\Network3\lib\mpj-v0_44"
echo   .\lib\mpj-v0_44\bin\mpjdaemon.bat -boot
