@echo off
rem Starts LanBall. Saved players and matches go in the lanball-v4-data folder next to this file.
cd /d "%~dp0"
java -jar LanBall-v7.jar
if errorlevel 1 pause
