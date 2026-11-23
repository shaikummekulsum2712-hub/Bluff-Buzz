@echo off
setlocal
echo ================================================================
echo   BLUFF ^& BUZZ -- MULTIPLAYER SERVER
echo ================================================================
echo.
echo  PORT 5000  =  Java Swing desktop client  (TCP Socket)
echo  PORT 8080  =  Browser / Mobile join page  (HTTP + WebSocket)
echo.
echo  HOW TO SHARE WITH OTHERS ON THE SAME WI-FI:
echo    1. Your browser will open automatically to http://localhost:8080/
echo    2. Look at the LAN IP shown in THAT browser page (e.g. 192.168.1.x)
echo    3. Tell friends to open http://192.168.1.x:8080/ on their phone/PC
echo    4. The browser page has a SCANNABLE QR code in the waiting room
echo.

set JAVA_BIN=java
if exist "C:\Program Files\Java\bin\java.exe" (
    set JAVA_BIN=C:\Program Files\Java\bin\java.exe
) else if exist "C:\Program Files\Common Files\Oracle\Java\javapath\java.exe" (
    set JAVA_BIN=C:\Program Files\Common Files\Oracle\Java\javapath\java.exe
)

rem Open browser after 2 second delay (server needs time to bind port 8080)
start "" cmd /c "timeout /t 2 /nobreak >nul && start http://localhost:8080/"

rem Start server in foreground (this window stays open)
"%JAVA_BIN%" -cp "bin;lib/*" com.fibbage.network.GameServer 5000 8080
pause
