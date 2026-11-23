# Test report

**Result: 0 tests executed. Nothing in this project has been run.**

This report exists so nobody mistakes written code for verified code. It records what was
built, what was checked statically, what could not be executed, and how to produce the real
results.

## 1. Environment of this build

| Item | Value |
|---|---|
| Android SDK | **not available** |
| JDK | **not available** |
| Gradle | **not available** |
| Physical Android device | **not available** |
| Emulator | **not available** |
| Network for CI | not used |

Because of that, the following were **not** performed and are **not** claimed:

- `gradle assembleDebug` / `assembleRelease` — no APK was produced.
- Any unit test run.
- Any instrumentation test run.
- Any manual device verification.
- SHA-256 of any APK.

What *was* done: the full source tree was written and reviewed for syntax, imports, module
dependencies, API usage against the declared library versions, and lifecycle/threading
correctness. Static review is not a substitute for execution.

## 2. Automated tests that exist

### JVM unit tests (run by CI)

| Module | File | Covers |
|---|---|---|
| `:core:common` | `FormattersTest` | byte/duration formatting, `FrameRateMeter` window maths |
| `:virtual-camera-sdk:format` | `FrameConfigurationTest` | buffer sizes, frame intervals, validation, presets |
| `:virtual-camera-sdk:format` | `YuvConverterTest` | BT.601 luma/chroma values for black, white and red; NV21 VU order; RGBA passthrough |
| `:virtual-camera-sdk:frames` | `FrameBufferPoolTest` | pool exhaustion, recycling, double-release safety |
| `:virtual-camera-sdk:frames` | `FrameDispatcherTest` | drop-oldest under a slow consumer, null consumer, consumer exceptions, `clear()` |

### Instrumentation tests (need a device)

| File | Covers |
|---|---|
| `app/src/androidTest/.../LibraryDatabaseTest` | Room insert/query/stats/rename/favourite/delete, test-session persistence |
| `app/src/androidTest/.../VirtualCameraPipelineTest` | session lifecycle, typed error for a missing file, idempotent `close()`, **continuous frames from an image source (end-to-end GL pipeline)**, repeated source switching |

## 3. Required acceptance cases

| # | Case | Status | Reason |
|---|---|---|---|
| 1 | APK installs on a compatible Android device | **BLOCKED** | no APK built, no device |
| 2 | Physical camera opens | **BLOCKED** | no device |
| 3 | Photo captured and saved | **BLOCKED** | no device |
| 4 | Video recorded and saved | **BLOCKED** | no device |
| 5 | Video imported from storage | **BLOCKED** | no device |
| 6 | Video plays in Virtual Camera Studio | **BLOCKED** | no device |
| 7 | Video selected as the SDK source | **BLOCKED** | no device |
| 8 | Sample app receives real frames | **BLOCKED** | no device |
| 9 | Image source produces continuous frames | **BLOCKED** (test written: `imageSourceDeliversContinuousFrames`) | no device |
| 10 | Switching camera/video/image | **BLOCKED** (test written: `switchingSourcesRepeatedlyDoesNotCrash`) | no device |
| 11 | Pause / resume / seek / loop | **BLOCKED** | no device |
| 12 | Measured FPS matches the target | **BLOCKED** | no device |
| 13 | Memory released after close | **BLOCKED** | no device |
| 14 | Close and reopen the app | **BLOCKED** | no device |
| 15 | Airplane mode | **BLOCKED** | no device; the app requests no INTERNET permission, so the expected result is "unchanged" |
| 16 | Camera permission denied | **BLOCKED** | no device; code path exists (`SourceError.PermissionDenied`, UI message) |
| 17 | Unsupported video file | **BLOCKED** (unit-level error path is testable on a device) | no device |
| 18 | Large video file | **BLOCKED** | no device |
| 19 | No crash on rapid source switching | **BLOCKED** (test written) | no device |
| 20 | No fake data anywhere | **REVIEWED** | all UI values are read from Room, disk, `CameraManager`, `MediaCodecList` or the SDK pipeline; no literals are displayed as measurements |

## 4. How to produce real results

```bash
# 1. Unit tests
gradle :core:common:testDebugUnitTest \
       :virtual-camera-sdk:format:testDebugUnitTest \
       :virtual-camera-sdk:frames:testDebugUnitTest

# 2. Build
gradle :app:assembleDebug :integration-sample:assembleDebug

# 3. Install on a device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r integration-sample/build/outputs/apk/debug/integration-sample-debug.apk

# 4. Instrumentation
gradle :app:connectedDebugAndroidTest

# 5. Checksum
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

The end-to-end acceptance run is:

1. Open VCAM Pro → Camera Studio → record a 10 s clip.
2. Library → the clip appears with its real size and duration.
3. Library → select the clip → "Use as virtual camera source".
4. Virtual Camera Studio → the preview shows the clip, FPS and delivered/dropped counters move.
5. Open VCAM Integration Sample → "Pick video" → the same clip → the preview shows the frames
   delivered by the SDK, with a live FPS readout.
6. "Save frame" → a PNG is written to the sample app's files directory.

If steps 4–6 do not show moving frames, the virtual camera is **not** proven working and must
not be described as such.

## 5. Known risks that testing must confirm

1. **GL read-back orientation.** The shader flips V after the SurfaceTexture transform matrix.
   This is correct for the common `diag(1,-1)` matrices; an unusual transform on a specific
   device could show a flipped or rotated image.
2. **Crop handling.** Decoded output uses the codec buffer size, so a padded stream
   (1920x1088) is reported at that size instead of the display crop.
3. **Pacing.** Output buffers are released with a future render timestamp; on a device with a
   slow decoder the frame rate may fall below the configured value (which is reported, not
   hidden).
4. **`glReadPixels` cost.** 720p30 RGBA read-back moves ≈110 MB/s. Low-end devices may need
   the 480p preset.
5. **CameraX rebinding.** Switching lens facing unbinds and rebinds every use case; this must
   be verified for leaks on a real device.
6. **Video recording stop latency.** `stopRecording()` polls for the output file for up to 5 s;
   a slow device may need longer.
