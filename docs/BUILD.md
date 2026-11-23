# Build guide

## Requirements

| Tool | Version |
|---|---|
| JDK | 17 |
| Gradle | 8.9 or newer |
| Android Gradle Plugin | 8.7.3 (declared in `gradle/libs.versions.toml`) |
| Kotlin | 2.0.21 |
| compileSdk / targetSdk | 35 |
| minSdk | 29 (Android 10) |
| Build tools | 35.x |

The repository deliberately ships **without** `gradle/wrapper/gradle-wrapper.jar` because it is
a binary artifact. Either install Gradle 8.9 locally, or generate the wrapper once:

```bash
gradle wrapper --gradle-version 8.9 --distribution-type bin
./gradlew :app:assembleDebug
```

## Common commands

```bash
# Debug APK for the main app and the sample
gradle :app:assembleDebug :integration-sample:assembleDebug

# JVM unit tests (no device needed)
gradle :core:common:testDebugUnitTest \
       :virtual-camera-sdk:format:testDebugUnitTest \
       :virtual-camera-sdk:frames:testDebugUnitTest

# Instrumentation tests (device or emulator required)
gradle :app:connectedDebugAndroidTest

# Everything
gradle build
```

Outputs:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk   # without signing secrets
integration-sample/build/outputs/apk/debug/integration-sample-debug.apk
```

## Release signing

No key material is ever committed. The `release` build type reads four environment variables:

| Variable | Meaning |
|---|---|
| `VCAM_KEYSTORE_PATH` | absolute path to a JKS/PKCS12 keystore |
| `VCAM_KEYSTORE_PASSWORD` | store password |
| `VCAM_KEY_ALIAS` | key alias |
| `VCAM_KEY_PASSWORD` | key password |

When `VCAM_KEYSTORE_PATH` is empty, Gradle produces an **unsigned** release APK, which is what
the CI does by default. An unsigned APK **cannot be installed** on a device — Android rejects
it. Until you configure signing, install the debug-signed APK instead
(`app-debug.apk`, published as `VCAM-Pro-debug.apk` in the release). To sign in CI, add the
secrets `VCAM_KEYSTORE_BASE64`, `VCAM_KEYSTORE_PASSWORD`, `VCAM_KEY_ALIAS`,
`VCAM_KEY_PASSWORD` to the repository.

Creating a key yourself:

```bash
keytool -genkeypair -v \
  -keystore vcam-release.jks -storetype JKS \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias vcam -dname "CN=VCAM Pro"
```

Never commit the keystore, and never share its password.

## APK metadata

- Application id: `com.vcam.pro` (debug builds append `.debug`)
- Version name / code: `1.0.0` / `1` — set in `app/build.gradle.kts`
- ABI filter: `arm64-v8a` only
- Checksums: the CI writes `artifacts/SHA256SUMS.txt` next to every APK

## CI

`.github/workflows/android.yml`:

1. provisions JDK 17 + Gradle 8.9,
2. runs the JVM unit tests,
3. assembles debug APKs for the app and the sample,
4. optionally decodes a signing key and assembles a signed release,
5. collects APKs and writes SHA-256 sums,
6. generates `website/releases.json`,
7. on a `v*` tag: publishes a GitHub Release with the APKs and `SHA256SUMS.txt`,
8. on `main`: deploys `website/` to GitHub Pages.

The instrumentation job is disabled (`if: false`) because the GitHub-hosted emulator has no
camera; enable it once a device runner exists.

## Enabling GitHub Pages

Repository → Settings → Pages → Source: **GitHub Actions**. The `pages` job then publishes
`website/` on every push to `main`.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| `SDK location not found` | set `ANDROID_HOME` or add `local.properties` with `sdk.dir=...` |
| `Unsupported class file major version` | the Gradle JVM is not 17; run `gradle -version` |
| Release APK is `-unsigned` | the signing environment variables are not set |
| `INSTALL_FAILED_NO_MATCHING_ABIS` | the device is not arm64; the APK is arm64-only by design |
| Camera preview is black | camera permission denied, or another app holds the camera |
