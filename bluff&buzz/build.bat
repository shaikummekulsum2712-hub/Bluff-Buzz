@echo off
setlocal
echo ========================================================
echo   Building BLUFF ^& BUZZ
echo ========================================================

set JAVAC_BIN=javac
if exist "C:\Program Files\Java\bin\javac.exe" (
    set JAVAC_BIN=C:\Program Files\Java\bin\javac.exe
) else if exist "C:\Program Files\Common Files\Oracle\Java\javapath\javac.exe" (
    set JAVAC_BIN=C:\Program Files\Common Files\Oracle\Java\javapath\javac.exe
)

if not exist bin (
    mkdir bin
)

echo Compiling Java source files...
dir /s /b src\*.java > sources.txt
"%JAVAC_BIN%" -encoding UTF-8 -cp "src;lib/*" -d bin @sources.txt
set BUILD_STATUS=%ERRORLEVEL%
del sources.txt

if %BUILD_STATUS% EQU 0 (
    echo [SUCCESS] Project compiled cleanly into bin/ directory!
    if exist web (
        if not exist bin\web mkdir bin\web
        xcopy /s /y /q web\* bin\web > nul
        echo [SUCCESS] Copied web client assets to bin/web/
    )
) else (
    echo [ERROR] Build failed with error code %BUILD_STATUS%
)
pause
