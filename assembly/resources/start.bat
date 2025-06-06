@echo off
setlocal

REM Get the directory of the script
set "BASEDIR=%~dp0"
set "CONFIGURATION=%BASEDIR%configuration\application.yml"
set "CLASSPATH=%BASEDIR%configuration\userlib"

set "JAR_PATH=%BASEDIR%internal\c7-data-migrator.jar"
set "COMMON_OPTS=-Dloader.path=%CLASSPATH% -Dspring.config.location=file:%CONFIGURATION%"

REM Count the arguments
set /a argCount=0
for %%A in (%*) do set /a argCount+=1

REM Check number of arguments
if "%argCount%" GTR "3" (
  echo Error: Too many arguments.
  goto :print_usage
)

for %%A in (%*) do (
  if /I not "%%~A"=="--runtime" if /I not "%%~A"=="--history" if /I not "%%~A"=="--list-skipped" if /I not "%%~A"=="--retry-skipped" (
    echo Invalid flag: %%A
    goto :print_usage
  )
)

REM No arguments
if "%argCount%" EQU "0" (
  echo Starting application without migration flags
  java %COMMON_OPTS% -jar "%JAR_PATH%"
  goto :end
) else (
  echo Starting migration with flags: %*
  java %COMMON_OPTS% -jar "%JAR_PATH%" %*
)

goto :end

:print_usage
echo.
echo Usage: start.bat [--runtime] [--history] [--list-skipped|--retry-skipped]
echo Options:
echo   --runtime          - Migrate runtime data only
echo   --history          - Migrate history data only
echo   --list-skipped     - List previously skipped data
echo   --retry-skipped    - Retry only previously skipped data
exit /b 1

:end
endlocal
