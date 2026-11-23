# Virtual Camera SDK — integration guide

The SDK delivers real frames from a video file, a still image or a physical camera to the
application that links it. It cannot inject frames into other applications; that is not
possible with public Android APIs.

## 1. Add the modules

```kotlin
// settings.gradle.kts
include(":virtual-camera-sdk:api")
include(":virtual-camera-sdk:format")
include(":virtual-camera-sdk:frames")
include(":virtual-camera-sdk:playback")
include(":virtual-camera-sdk:source")

// app/build.gradle.kts
dependencies {
    implementation(project(":virtual-camera-sdk:source"))   // pulls the rest
    implementation("androidx.camera:camera-lifecycle:1.4.1") // required by the camera source
}
```

`minSdk` must be 29 or higher.

## 2. Public API

| Type | Purpose |
|---|---|
| `VirtualCameraSdk` | factory: `createSession`, `videoSource`, `imageSource`, `physicalCameraSource` |
| `CameraSession` | owns one active source and one consumer; serialises source switches |
| `CameraSource` | `open` / `start` / `stop` / `close`, `state`, `capabilities`, `playbackController` |
| `FrameConsumer` | `onFrame(Frame)`; every frame must be released |
| `Frame` | `data: ByteBuffer`, `width`, `height`, `format`, `timestampNanos`, `mediaTimestampUs`, `sequenceNumber`, `release()` |
| `FrameConfiguration` | `width`, `height`, `fps`, `format`, `scaleMode` |
| `FrameFormat` | `RGBA_8888`, `YUV_420_888` (I420), `NV21` |
| `ScaleMode` | `FIT`, `CROP`, `FILL` |
| `PlaybackController` | `play`, `pause`, `seekTo`, `seekBy`, `setLooping`, `playback: StateFlow<PlaybackState>` |
| `SourceState` | `Idle`, `Opening`, `Ready`, `Running`, `Paused`, `Completed`, `Failed`, `Closed` |
| `SourceError` | typed failures: `UnsupportedMedia`, `DecoderInitFailed`, `PermissionDenied`, … |

## 3. Minimal integration

```kotlin
class CameraScreen : ComponentActivity() {

    private lateinit var session: CameraSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = VirtualCameraSdk.createSession(
            config = VirtualCameraConfig(
                configuration = FrameConfiguration(1280, 720, 30, FrameFormat.RGBA_8888),
                bufferCount = 4,
            ),
            consumer = FrameConsumer { frame ->
                // Runs on a pipeline thread, never on the main thread.
                // Copy what you need, then always release.
                renderToSurface(frame)
                frame.release()
            },
        )

        lifecycleScope.launch {
            session.useSource(VirtualCameraSdk.videoSource(this@CameraScreen, uri))
        }
    }

    override fun onDestroy() {
        session.close()
        super.onDestroy()
    }
}
```

## 4. Choosing a source

```kotlin
// Video file — decoded as a stream, never fully buffered.
session.useSource(VirtualCameraSdk.videoSource(context, uri, displayName = "clip.mp4"))

// Still image — re-rendered at the configured FPS.
session.useSource(VirtualCameraSdk.imageSource(context, uri))

// Live camera — requires CAMERA permission and a LifecycleOwner.
session.useSource(
    VirtualCameraSdk.physicalCameraSource(
        context = context,
        lifecycleOwner = this,
        lensFacing = CameraSelector.LENS_FACING_BACK,
    ),
)
```

Switching sources is safe to call repeatedly: every transition is serialised behind a mutex,
and the previous source is stopped and released before the next one opens.

## 5. Transport

```kotlin
val playback = session.playback()      // null for the live camera
playback?.play()
playback?.pause()
playback?.seekTo(1_500_000L)           // microseconds
playback?.setLooping(true)

lifecycleScope.launch {
    playback?.playback?.collect { state ->
        // state.positionUs, state.durationUs, state.isPlaying, state.looping
    }
}
```

## 6. Changing resolution / FPS / format

Sources are single-use, so `updateConfiguration` releases the active source:

```kotlin
session.updateConfiguration(config.copy(width = 1920, height = 1080))
// then attach a new source, built with the new configuration
session.useSource(VirtualCameraSdk.videoSource(context, uri))
```

## 7. Frame ownership and backpressure

- `FrameBufferPool` holds a fixed number of direct buffers (`bufferCount`, default 4).
- `FrameDispatcher` keeps **one** frame pending. If the consumer is slower than the producer,
  the older frame is released and counted as dropped.
- A frame that is never released permanently removes a buffer from the pool; after
  `bufferCount` leaks the pipeline drops everything. Always release.
- `session.frameStats` reports `delivered` and `dropped` so you can see it happen.

## 8. Threading rules

| Callback | Thread |
|---|---|
| `FrameConsumer.onFrame` | source pipeline thread (video: dedicated decode thread, image: render thread, camera: analysis thread) |
| `CameraSource.state` / `PlaybackController.playback` | any (StateFlow) |
| all `suspend` functions | any dispatcher; internally marshalled |

Never block inside `onFrame`. Do the minimum work and copy out if you need the pixels later.

## 9. Errors

```kotlin
session.useSource(source).onFailure { error ->
    when (error) {
        is SourceError.PermissionDenied -> askForCameraPermission()
        is SourceError.UnsupportedMedia -> showUnsupportedFile(error.message)
        is SourceError.UnsupportedConfiguration -> fallBackTo720p()
        else -> showGenericError(error)
    }
}
```

`session.state` becomes `SessionState.Failed` at the same time, so a UI that only observes the
flow still learns about the failure.

## 10. Performance notes

- RGBA output is the cheapest: one GL read-back per frame.
- `YUV_420_888` and `NV21` add a full CPU pass over the frame (≈110 MB/s at 720p30).
- 1080p60 roughly quadruples read-back traffic versus 720p30; use it only when the device
  reports the capability (see the Device Center screen for real numbers).
- Decoded video output uses the codec buffer size; a padded stream (1920x1088) is reported as
  such rather than silently cropped.
