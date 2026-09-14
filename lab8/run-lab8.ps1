param(
    [Parameter(Position = 0)][string]$Groups = "2",
    [Parameter(Position = 1)][string]$N = "1024",
    [Parameter(Position = 2)][string]$Mode = "nonblocking",
    [Parameter(Position = 3)][string]$Np = "4",
    [Parameter(Position = 4)][string]$Strips = "8",
    [Parameter(Position = 5)][string]$DataDir = "data"
)

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$MpjHome = Join-Path $Root "lib\mpj-v0_44"
$Starter = Join-Path $MpjHome "lib\starter.jar"
$Classes = Join-Path $Root "target\classes"
$Report = Join-Path $env:TEMP "lab8-console.txt"

$env:MPJ_HOME = $MpjHome
$env:PATH = "$MpjHome\bin;" + $env:PATH

Write-Host "MPJ_HOME=$MpjHome"
Write-Host "groups=$Groups n=$N mode=$Mode np=$Np strips=$Strips datadir=$DataDir"

& mvn -q -f (Join-Path $Root "pom.xml") compile
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

# Первые -D относятся к стартеру, вторые (после -cp) — к рабочим процессам MPJ.
$javaArgs = @(
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
    "-jar", $Starter,
    "-np", $Np,
    "-cp", $Classes,
    "-Dfile.encoding=UTF-8",
    "-Dstdout.encoding=UTF-8",
    "-Dstderr.encoding=UTF-8",
    "org.example.lab8.Lab8App",
    $Groups, $N, $Mode, $Strips, $DataDir
)

if (Test-Path $Report) {
    Remove-Item $Report -Force
}

$process = Start-Process -FilePath "java" -ArgumentList $javaArgs `
    -WorkingDirectory $Root -Wait -NoNewWindow -PassThru `
    -RedirectStandardOutput $Report

$utf8 = New-Object System.Text.UTF8Encoding $false
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
Get-Content -Path $Report -Encoding UTF8 | ForEach-Object { Write-Host $_ }

exit $process.ExitCode
