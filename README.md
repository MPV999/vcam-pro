# VCAM PRO ULTIMATE VIP 2026

On-device virtual camera studio and SDK for Android.

VCAM Pro records with the real camera, keeps a private media library, and exposes the frames
you pick to **apps that integrate the VCAM Virtual Camera SDK**. Everything runs on the
device: no backend, no account, no VPS, no paid API.

> **Scope, stated plainly.** Android has no public API that lets an ordinary app replace the
> system camera for *other* apps. This project therefore delivers a virtual camera **inside**
> the host application that links the SDK — the main app and the sample integration app.
> There is no root, no Magisk/Xposed/LSPosed, no Frida, no hooking, no HAL patching, and
> nothing that touches banking, wallet, KYC, biometric or liveness systems.

---

## Repository layout

```
app/                        VCAM Pro application (Compose, Material 3)
core/
  common/                   dispatchers, formatters, logging, frame-rate meter
  database/                 Room entities, DAOs, MediaRepository
  storage/                  app-private media store, MediaStore export, SAF import, DataStore settings
  security/                 Android Keystore AES-GCM vault, SHA-256 helpers
  media/                    MediaMetadataRetriever probing, thumbnail cache
  camera/                   CameraX wrapper: preview, still capture, video capture
features/
  dashboard/                real library/device statistics
  camera/                   Camera Studio
  media_library/            library grid, search, rename, delete, favourite, import/export
  virtual_camera/           Virtual Camera Studio (an SDK client)
  playback/                 Media3 preview player
  settings/                 theme, language, pipeline defaults
  device_center/            camera ids, resolutions, fps ranges, codecs, memory
virtual-camera-sdk/
  api/                      CameraSource, FrameConsumer, PlaybackController, CameraSession
  format/                   FrameConfiguration, FrameFormat, YuvConverter
  frames/                   Frame, FrameBufferPool, FrameDispatcher (backpressure)
  playback/                 EGL/GL compositor + streaming MediaCodec decoder
  source/                   VideoFileSource, ImageFileSource, PhysicalCameraSource, session
integration-sample/         separate APK that integrates only the SDK
website/                    static download site (GitHub Pages)
.github/workflows/          CI: tests, APKs, release, checksums, Pages
docs/                       build, SDK integration, feature status, test report
```

## Build

```bash
gradle :app:assembleDebug
gradle :app:assembleRelease
gradle :core:common:testDebugUnitTest \
       :virtual-camera-sdk:format:testDebugUnitTest \
       :virtual-camera-sdk:frames:testDebugUnitTest
```

The repository does not contain a `gradle/wrapper/gradle-wrapper.jar` (a binary). Use a local
Gradle 8.9+ install, or let CI provision it (the workflow does). See
[docs/BUILD.md](docs/BUILD.md).

## Status

Read [docs/TEST_REPORT.md](docs/TEST_REPORT.md) before trusting any claim in this README.
The code is written and reviewed, but **no build, unit test or device test has been executed
in the environment where it was written** — there is no Android SDK, JDK or device there.
The first CI run is what turns "written" into "verified".

## Documentation

- [docs/SDK_INTEGRATION.md](docs/SDK_INTEGRATION.md) — integrate the virtual camera SDK
- [docs/BUILD.md](docs/BUILD.md) — local and CI builds, signing, releases
- [docs/FEATURES.md](docs/FEATURES.md) — what is done, what is partial, what is missing and why
- [docs/TEST_REPORT.md](docs/TEST_REPORT.md) — test inventory and honest results

## Licence and responsible use

Use this only with apps you own or are authorised to test. Do not use it to defeat identity
verification, liveness detection, KYC or any anti-fraud system.
