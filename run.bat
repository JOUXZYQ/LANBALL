@cd /d "%~dp0"
@java --enable-native-access=javafx.graphics,javafx.media --module-path lib --add-modules javafx.controls,javafx.media -cp target\lanball-v4-4.1.0.jar lanball.LanBall
@if errorlevel 1 pause
