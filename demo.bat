@echo off
rem Rehearsal: two windows on one laptop, separate demo-data folder, ports 45889/45891.
cd /d "%~dp0"
java -cp LanBall-v9.jar lanball.Demo
if errorlevel 1 pause
