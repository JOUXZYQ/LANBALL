@cd /d "%~dp0"
@call mvn -q package dependency:copy-dependencies -DoutputDirectory=lib
