@echo off
rem Builds LanBall-v9.jar from src\ and resources\. Needs JDK 17 or newer.
setlocal
cd /d "%~dp0"
if exist build rmdir /s /q build
mkdir build\classes\mobile
javac --release 17 -encoding UTF-8 -d build\classes src\lanball\*.java || goto :fail
copy /y resources\mobile\* build\classes\mobile\ >nul || goto :fail
jar --create --file LanBall-v9.jar --main-class lanball.LanBall -C build\classes . || goto :fail
echo Built LanBall-v9.jar
exit /b 0
:fail
echo BUILD FAILED
exit /b 1
