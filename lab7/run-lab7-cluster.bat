@echo off
setlocal
set "ROOT=%~dp0"
set "MPJ_HOME=%ROOT%lib\mpj-v0_44"
set "PATH=%MPJ_HOME%\bin;%PATH%"

set "MODE=%~1"
if "%MODE%"=="" set "MODE=blocking"
set "N=%~2"
if "%N%"=="" set "N=1024"
set "NP=%~3"
if "%NP%"=="" set "NP=2"
set "STRIPS=%~4"
if "%STRIPS%"=="" set "STRIPS=8"

if not exist "%ROOT%machines" (
    echo Create file "machines" with one IP per line.
    exit /b 1
)

echo MPJ_HOME=%MPJ_HOME%
echo mode=%MODE% n=%N% np=%NP% strips=%STRIPS%
echo machines:
type "%ROOT%machines"

call mvn -q -f "%ROOT%pom.xml" -DskipTests package
if errorlevel 1 exit /b 1

rem MPJ daemon treats \t in Windows paths as a tab. Use forward slashes.
set "ROOT_UNIX=%ROOT:\=/%"
set "JAR=%ROOT_UNIX%target/Network3-1.0-SNAPSHOT.jar"
set "MACHINES=%ROOT_UNIX%machines"

if not exist "%ROOT%target\Network3-1.0-SNAPSHOT.jar" (
    echo JAR not found: %ROOT%target\Network3-1.0-SNAPSHOT.jar
    exit /b 1
)

echo jar=%JAR%
java -jar "%MPJ_HOME%\lib\starter.jar" -np %NP% -dev niodev -wdir "%ROOT_UNIX%" -machinesfile "%MACHINES%" -jar "%JAR%" %MODE% %N% %STRIPS%
