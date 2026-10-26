@echo off
rem Rebuilds the JAR, then runs every Java test against it.
rem Close any running LanBall first: the JAR smoke test uses ports 45888 and 45890.
rem "test.bat deep" also runs the long adversarial physics search (a few minutes).
setlocal
cd /d "%~dp0"
call "%~dp0build.bat" || exit /b 1
set "T=%TEMP%\lanball-test-%RANDOM%%RANDOM%"
mkdir "%T%\classes"
javac --release 17 -nowarn -encoding UTF-8 -cp LanBall-v9.jar -d "%T%\classes" tests\lanball\*.java || goto :fail
set "CP=LanBall-v9.jar;%T%\classes"
for %%t in (ProjectTest GameplayTest RecoveryTest MobileTest ReviewTest) do (
  echo === %%t
  java -cp "%CP%" lanball.%%t "%T%" || goto :fail
)
echo === PhysicsTest ^(old v7/v8 bug reproductions^)
java -cp "%CP%" lanball.PhysicsTest || goto :fail
echo === ReplaySizeTest
java -cp "%CP%" lanball.ReplaySizeTest || goto :fail
echo === UiFlowTest ^(opens real windows^)
java -cp "%CP%" lanball.UiFlowTest "%T%\ui" || goto :fail
echo === JarSmokeTest
java -cp "%CP%" lanball.JarSmokeTest "%T%\smoke" || goto :fail
if /i "%~1"=="deep" (
  echo === DeepPhysics ^(read the SEARCH line: cycles=0 outside=0 nonfinite=0^)
  java -Xmx512m -cp "%CP%" lanball.DeepPhysics "%T%\deep" || goto :fail
)
echo.
echo ALL TESTS PASSED. Temporary data: %T%
exit /b 0
:fail
echo TEST FAILED. Temporary data: %T%
exit /b 1
