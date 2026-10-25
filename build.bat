@echo off
rem Builds LanBall-v7.jar with the JDK only (Java 8 or newer). No Maven, no libraries.
cd /d "%~dp0"
if exist out rmdir /s /q out
javac -d out src\lanball\*.java || goto failed
jar cfe LanBall-v7.jar lanball.LanBall -C out . || goto failed
echo Built LanBall-v7.jar
goto :eof
:failed
pause
