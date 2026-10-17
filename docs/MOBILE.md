# Native Android and iPhone builds

The shared JavaFX client has touch input and a responsive layout. The Maven project
provides `android`, `ios` and `ios-simulator` profiles using GluonFX 1.0.29. These targets
are configured but have **not been compiled or tested on phones in this delivery**.

This is Java application source using JavaFX. GluonFX compiles it to native platform
binaries; phones do not run a Swing desktop JAR. There is no browser interface.

## Android

Use Linux or Windows WSL2 with the Gluon-supported GraalVM/JDK toolchain. The plugin can
download the required Android SDK/NDK. Follow the [Gluon Android prerequisites](https://docs.gluonhq.com/#_android).

From the project directory:

```text
mvn -Pandroid gluonfx:build gluonfx:package
mvn -Pandroid gluonfx:install gluonfx:nativerun
```

The second command needs a connected device with USB debugging. The generated APK is
under `target/gluonfx/aarch64-android/gvm/`. The toolchain's generated Android manifest
includes `android.permission.INTERNET`, which direct TCP connections need. Debug signing
is suitable for local testing; store distribution needs release signing.

## iPhone / iPad

Use a Mac with Xcode, a compatible Gluon GraalVM toolchain, and an Apple signing identity
and provisioning profile. Windows alone cannot produce this signed iPhone build. See
[Gluon iOS setup](https://docs.gluonhq.com/#_ios), including the free-provisioning option.

```text
mvn -Pios gluonfx:build gluonfx:package
mvn -Pios gluonfx:install gluonfx:nativerun
```

Use bundle identifier **`org.lanball.v4`** in the matching provisioning configuration.
The project includes `src/main/resources/META-INF/substrate/ios/Partial-Info.plist` with
`NSLocalNetworkUsageDescription`; it explains why LanBall connects to a LAN host. Allow
that permission on the device. The key is described in [Apple's documentation](https://developer.apple.com/documentation/bundleresources/information-property-list/nslocalnetworkusagedescription).

For simulator work the profile is `ios-simulator`. A simulator is not a substitute for
testing Wi-Fi and simultaneous touches on real phones.

## Device acceptance test still required

1. Install the resulting app on one Android phone and one iPhone.
2. Connect both phones and the Windows host to the same LAN; permit private-network traffic.
3. Start the desktop host. On each phone enter the displayed IPv4 address and register a
   separate profile. Paste the independently verified host fingerprint before joining. Select opposing teams, mark ready, then start from the computer.
4. Hold movement and kick together. Confirm score updates agree on every screen.
5. Background a phone and verify that its movement stops. Rejoin using the existing profile.
6. Complete a round, compare standings, restart the desktop host, and check the saved totals.

No mobile binary or successful device-test claim is implied by the desktop tests or screenshots.

In v4.1 the connection uses TLS 1.3 and mandatory certificate pinning. Mobile TLS,
audio and native-image configuration still need platform build validation. Desktop
hosting requires JDK keytool; mobile builds are intended as joining clients.

