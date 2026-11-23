@echo off
setlocal
echo ========================================================
echo   Running Standalone Tests for Blocks 1, 2, and 3
echo ========================================================

set JAVA_BIN=java
if exist "C:\Program Files\Java\bin\java.exe" (
    set JAVA_BIN=C:\Program Files\Java\bin\java.exe
) else if exist "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe" (
    set JAVA_BIN=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
)

echo [TEST 1] Testing Block 1 - All 4 OOP Pillars...
"%JAVA_BIN%" -cp "bin;lib/*" com.fibbage.model.GameRound
echo.

echo [TEST 2] Testing Block 2 - JDBC SQLite Database Layer...
"%JAVA_BIN%" -cp "bin;lib/*" com.fibbage.db.DatabaseManager
echo.

echo [TEST 3] Testing Block 3 - Threading, Mutex Buzzer Locks ^& Timers...
"%JAVA_BIN%" -cp "bin;lib/*" com.fibbage.concurrency.BuzzerController
echo.

echo All Block Tests Completed!
pause
