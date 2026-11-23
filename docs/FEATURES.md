# Feature status

Legend: **Done** = implemented in source and reviewable. **Partial** = implemented with a
documented gap. **Missing** = not implemented, with the reason.

Nothing in this table has been executed on a device in this environment; see
[TEST_REPORT.md](TEST_REPORT.md).

## Application

| Feature | Status | Notes |
|---|---|---|
| Kotlin + Jetpack Compose + Material 3 | Done | Compose BOM 2024.12.01, Material 3 1.3.1 |
| Material 3 **Expressive** styling | Partial | Expressive components/motion are alpha-only in stable Material 3; the look is approximated with a custom dark palette and typography in `ui/theme/Theme.kt` |
| Premium dark UI, light/dark support | Done | `ThemeMode.SYSTEM/LIGHT/DARK` in settings |
| Vietnamese + English | Done | `values/` and `values-vi/` in every module; applied through `LocaleApplier` + activity restart |
| Dashboard with real statistics | Done | Counts, library size, disk usage, device facts, recent media, recent test sessions — all read from Room / disk / `CameraManager` |
| Performance chart | Missing | Only numeric FPS / delivered / dropped counters exist; no chart component |
| Camera Studio (photo + video) | Done | CameraX `Preview` + `ImageCapture` + `VideoCapture`/`Recorder` |
| Front/back switch, zoom, torch, exposure API | Done | Capabilities queried from the bound camera; unsupported controls report why |
| Timer, grid | Done | Timer 0/3/10 s, grid overlay |
| Resolution / FPS selection for capture | Partial | Quality presets SD/HD/FHD/4K with automatic fallback; no manual FPS selection for capture |
| Permission-denied handling | Done | Message in the preview area + no crash |
| Media library | Done | Grid, thumbnails, search, filters, rename, delete, favourite, details, import (SAF), export (MediaStore) |
| MediaStore export | Done | Scoped storage, `IS_PENDING` protocol, no permission needed |
| Virtual Camera Studio | Done | Source picker, play/pause/seek/loop, scale mode, output size, FPS, live preview of SDK frames, measured FPS, delivered/dropped, pipeline log |
| Test-session recording | Done | Written to Room on stop/close and shown on the dashboard |
| Settings | Done | Theme, language, keep-screen-on, grid, mirror, default FPS, privacy text |
| Device Center | Done | Camera ids, facing, hardware level, max JPEG, AE fps ranges, flash, sensor orientation, video codecs, memory class, display |
| Offline, no account, no backend | Done | No INTERNET permission requested by the app |
| WorkManager housekeeping | Done | Daily orphan-file cleanup with real file deletion |
| Android Keystore | Done | AES-256-GCM vault used for on-device diagnostics |
| Room + DataStore | Done | Schema exported (`core/database/schemas`) |

## Virtual Camera SDK

| Feature | Status | Notes |
|---|---|---|
| `CameraSource` / `PhysicalCameraSource` / `VideoFileSource` / `ImageFileSource` | Done | All three source kinds implemented |
| `FrameConsumer`, `FrameConfiguration`, `PlaybackController`, `SourceState`, `CameraSession` | Done | Public API in `:virtual-camera-sdk:api` |
| Real-time frame delivery with accurate timestamps | Done | Monotonic `timestampNanos` + `mediaTimestampUs` from `SurfaceTexture` |
| FPS control | Done | Frame-interval gate in the decoder + render loop pacing for images |
| Source switching without crashes | Done | Serialised behind a `Mutex`; previous source fully released first |
| Backpressure, bounded queues | Done | Fixed buffer pool + single-slot dispatcher with drop-oldest |
| Lifecycle and resource release | Done | `close()` is idempotent; codec/surface/EGL released on the owning thread |
| Decode errors, missing source | Done | Typed `SourceError` values, no silent stalls |
| YUV_420_888 / NV21 / RGBA output | Done | GL read-back + BT.601 CPU conversion |
| Streaming decode (never whole file in RAM) | Done | `MediaExtractor` → `MediaCodec` → `SurfaceTexture` → GL |
| OpenGL ES compositing, FIT/CROP/FILL | Done | Custom EGL core + FBO + `glReadPixels` |
| Media3 usage | Partial | Media3 drives the *preview player* in the studio; the SDK pipeline uses MediaCodec directly, which is required to obtain raw frames |

## Sample integration app

| Feature | Status | Notes |
|---|---|---|
| Separate APK that links only the SDK | Done | `:integration-sample` |
| Preview of physical camera / video / image through the SDK | Done | One code path for all three |
| Source switching UI | Done | Back camera, front camera, video picker, image picker |
| FPS + resolution + delivered/dropped display | Done | Computed from real frame timestamps |
| Save a frame as PNG | Done | Proves the pixels are real |
| Record a video from the SDK pipeline | Missing | Would need an encoder fed from RGBA frames; the pipeline delivers frames but the recorder is not implemented. Not claimed as working. |
| Event log, close/reopen session | Done | Buttons call `session.close()` and recreate it |

## Delivery

| Item | Status | Notes |
|---|---|---|
| Android source | Done | 20 Gradle modules |
| Built APK | **Not built** | No Android SDK/JDK in this environment; CI builds it |
| SHA-256 | **Not produced yet** | CI writes `SHA256SUMS.txt` per run |
| GitHub Actions workflow | Done | `.github/workflows/android.yml` |
| Website | Done | `website/`, reads the newest release from the GitHub API; set `owner`/`repo` in `website/config.js` |
| Unit tests | Written, not run | `:core:common`, `:virtual-camera-sdk:format`, `:virtual-camera-sdk:frames` |
| Instrumentation tests | Written, not run | Needs a device with a camera |
| Test report | Done | [TEST_REPORT.md](TEST_REPORT.md) |

## Deliberately not implemented

| Item | Reason |
|---|---|
| Replacing the system camera for arbitrary apps | No public Android API exists. Requires root / HAL modification / hooking, which the project explicitly forbids |
| Magisk / Xposed / LSPosed / Frida integration | Forbidden by the project scope |
| Bypassing liveness, KYC or identity checks | Forbidden by the project scope |
| Cloud sync, accounts, telemetry | Contradicts the offline-first requirement |
