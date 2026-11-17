@echo off
setlocal

set "DIR=%~dp0"
set "MAVEN_HOME=%DIR%.mvn\apache-maven-3.9.9"

if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [ReqWise] Bootstrapping Apache Maven 3.9.9...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $zip = Join-Path $env:TEMP 'apache-maven-3.9.9-bin.zip'; (New-Object Net.WebClient).DownloadFile('https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip', $zip); Expand-Archive -Path $zip -DestinationPath (Join-Path '%DIR%' '.mvn') -Force; Remove-Item $zip -Force }"
)

if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    call "%MAVEN_HOME%\bin\mvn.cmd" %*
    exit /b %ERRORLEVEL%
)

echo [ReqWise] Error: Failed to bootstrap Maven.
exit /b 1
