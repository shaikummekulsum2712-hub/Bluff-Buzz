@echo off
setlocal
echo ========================================================
echo   Launching BLUFF ^& BUZZ (Party GUI)
echo ========================================================

set JAVA_BIN=java
if exist "C:\Program Files\Java\bin\java.exe" (
    set JAVA_BIN=C:\Program Files\Java\bin\java.exe
) else if exist "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe" (
    set JAVA_BIN=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
)

REM Launch GUI
start "" "%JAVA_BIN%" -cp "bin;lib/*" com.fibbage.ui.MainFrame
