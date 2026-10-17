# LanBall v4.1 regression harness

Separate testing material, excluded from the application source count. Recorded results:
49 server/network checks and 15 JavaFX interface checks passed on Windows x64 with
Temurin JDK 21 and JavaFX 21.0.12. The text logs are from those runs.

Build the application from the repository root first. Close running LanBall hosts
before the UI test. Run these commands in PowerShell from this tests folder, with JDK 21
java/javac on PATH:

```powershell
$app = (Resolve-Path '..').Path
$cp = "$app\target\lanball-v4-4.1.0.jar;classes"
New-Item -ItemType Directory -Path classes -Force | Out-Null
javac --module-path "$app\lib" --add-modules javafx.controls,javafx.media -cp $cp -d classes Checks.java UiChecks.java
java --module-path "$app\lib" --add-modules javafx.controls,javafx.media -cp $cp lanball.Checks test-data
java --enable-native-access=javafx.graphics,javafx.media --module-path "$app\lib" --add-modules javafx.controls,javafx.media -cp $cp lanball.UiChecks screenshots
```

The tests generate isolated data, certificates and screenshots in the supplied output
folders. Test credentials and identities are disposable. They never use an existing
league. UI tests temporarily redirect user.home before constructing the app. Synthetic
touch tests do not establish real Android/iPhone support; native builds and physical Wi-Fi
testing are still outstanding. See the application's docs/VALIDATION.md for scope.

