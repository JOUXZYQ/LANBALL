@echo off
rem Starts LanBall. Click Host on one laptop; others click Join or open the Phones address.
cd /d "%~dp0"
java -jar LanBall-v9.jar
if errorlevel 1 pause
