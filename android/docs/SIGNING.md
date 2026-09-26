# DustUI permanent personal signing

DustUI uses the same external personal-signing pattern as BKX.

Default external files:

```text
~/.config/devtool/dustui/android-signing.properties
~/.config/devtool/dustui/dustui-personal.p12
```

Create a dedicated DustUI identity once:

```bash
bash android/tools/setup-personal-signing.sh
```

Check configuration without exposing passwords:

```bash
cd android
./gradlew :app:dustuiSigningStatus
./gradlew :app:verifyDustuiPersonalSigningConfigured
```

The Release build fails at `preReleaseBuild` if signing is unavailable, before expensive R8/package work.

## Reusing the BKX personal certificate

Android permits different application IDs to use the same signing certificate. If you intentionally want one personal certificate for BKX and DustUI, point DustUI at BKX's existing external properties file:

```bash
export DUSTUI_ANDROID_SIGNING_PROPERTIES="$HOME/.config/devtool/bkx/android-signing.properties"
./devtoolw gate-e-release
```

Do this only if DustUI has not already established a different permanent release signing identity. Changing certificates prevents in-place updates of an already-installed APK signed by the old certificate.

No keystore or password properties belong in this repository.
