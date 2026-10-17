# Source accounting

Physical lines, including blank lines, comments, imports and declarations:

| File | Lines | Responsibility |
|---|---:|---|
| src/main/java/lanball/Arena.java | 99 | Rendering, interpolation, audio |
| src/main/java/lanball/LanBall.java | 213 | Main interface, connection, input |
| src/main/java/lanball/League.java | 141 | Profiles, records, reports, persistence |
| src/main/java/lanball/Panels.java | 166 | League, history, replay, settings, tournaments |
| src/main/java/lanball/Server.java | 301 | Authoritative simulation, networking, matches |
| src/main/java/lanball/Wire.java | 75 | TLS identity, certificate pinning, messages, hashing |
| **Java total** | **995** | |
| run.bat | 3 | Windows launch |
| build.bat | 2 | Maven build |
| **Application plus scripts** | **1,000** | |

Maven XML and iOS permission metadata are additional build configuration. WAV assets,
third-party JavaFX libraries, documentation and the separate regression harness are not
application source and are excluded. No game implementation is generated or hidden in
resources. Java strings contain the UI styling and are already counted.

From the extracted application folder, verify with PowerShell:

```powershell
Get-ChildItem src/main/java/lanball/*.java | ForEach-Object {
    '{0}: {1}' -f $_.Name, (Get-Content -LiteralPath $_.FullName).Count
}
(Get-Content run.bat).Count
(Get-Content build.bat).Count
```

The implementation uses descriptive classes and methods, with some short related
statements on the same line. It is compact code, not a claim that 1,000 lines can preserve
every behavior of the much larger v3 implementation. See V3_COMPARISON.md.
