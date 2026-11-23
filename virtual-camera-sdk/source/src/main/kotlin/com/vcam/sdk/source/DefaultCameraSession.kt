package com.vcam.sdk.source

import com.vcam.core.common.FrameRateMeter
import com.vcam.core.common.MonotonicClock
import com.vcam.core.common.VcamLog
import com.vcam.sdk.api.CameraSession
import com.vcam.sdk.api.CameraSource
import com.vcam.sdk.api.PlaybackController
import com.vcam.sdk.api.SessionState
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.VirtualCameraConfig
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.frames.FrameStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Default [CameraSession]: serialises every source transition behind a mutex so that
 * hammering "switch source" cannot interleave teardown and setup (the classic source of
 * `IllegalStateException: Surface has been released` crashes).
 *
 * Lifecycle: [close] releases the active source asynchronously and then cancels the
 * session scope. Create a new session to stream again.
 */
class DefaultCameraSession(
    initialConfiguration: FrameConfiguration = FrameConfiguration.P720,
    private val bufferCount: Int = 4,
    private val autoStart: Boolean = true,
) : CameraSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val transitionMutex = Mutex()
    private val rateMeter = FrameRateMeter()
    private val deliveredCount = AtomicLong(0)
    private val droppedCount = AtomicLong(0)

    @Volatile
    private var lastFrameNanos: Long = 0L

    @Volatile
    private var consumer: FrameConsumer? = null

    @Volatile
    private var closed = false

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _configuration = MutableStateFlow(initialConfiguration)
    override val configuration: StateFlow<FrameConfiguration> = _configuration.asStateFlow()

    private val _activeSource = MutableStateFlow<CameraSource?>(null)
    override val activeSource: StateFlow<CameraSource?> = _activeSource.asStateFlow()

    private val _frameStats = MutableStateFlow(FrameStats())
    override val frameStats: StateFlow<FrameStats> = _frameStats.asStateFlow()

    private val statsJob: Job = scope.launch {
        while (true) {
            delay(500)
            _frameStats.value = FrameStats(
                delivered = deliveredCount.get(),
                dropped = droppedCount.get(),
                lastFrameNanos = lastFrameNanos,
            )
        }
    }

    /** Wraps the integration consumer so the session can report real counters. */
    private val countingConsumer = FrameConsumer { frame ->
        rateMeter.onFrame()
        deliveredCount.incrementAndGet()
        lastFrameNanos = frame.timestampNanos
        val target = consumer
        if (target == null) {
            frame.release()
        } else {
            try {
                target.onFrame(frame)
            } catch (throwable: Throwable) {
                droppedCount.incrementAndGet()
                frame.release()
                VcamLog.e(TAG, "Consumer threw on frame #${frame.sequenceNumber}", throwable)
            }
        }
    }

    override fun setConsumer(consumer: FrameConsumer?) {
        this.consumer = consumer
        _activeSource.value?.setFrameConsumer(countingConsumer)
    }

    override suspend fun useSource(source: CameraSource): Result<Unit> = transitionMutex.withLock {
        if (closed) return@withLock Result.failure(SourceError.Internal("Session is closed"))
        try {
            _state.value = SessionState.Starting
            releaseActiveSource()
            source.setFrameConsumer(countingConsumer)
            source.open(_configuration.value)
            if (autoStart) source.start()
            _activeSource.value = source
            rateMeter.reset()
            _state.value = SessionState.Streaming(source.kind)
            VcamLog.i(TAG, "Source attached: ${source.id}")
            Result.success(Unit)
        } catch (error: SourceError) {
            _activeSource.value = null
            _state.value = SessionState.Failed(error)
            VcamLog.e(TAG, "Cannot attach source ${source.id}", error)
            Result.failure(error)
        } catch (throwable: Throwable) {
            val error = SourceError.Internal("Cannot attach source: ${throwable.message}", throwable)
            _activeSource.value = null
            _state.value = SessionState.Failed(error)
            VcamLog.e(TAG, "Cannot attach source ${source.id}", throwable)
            Result.failure(error)
        }
    }

    override suspend fun updateConfiguration(configuration: FrameConfiguration): Result<Unit> =
        transitionMutex.withLock {
            if (closed) return@withLock Result.failure(SourceError.Internal("Session is closed"))
            if (configuration == _configuration.value) return@withLock Result.success(Unit)
            _configuration.value = configuration
            // Sources are single-use, so the active one is released. Attach a new source
            // built with the new configuration.
            releaseActiveSource()
            _state.value = SessionState.Stopped
            Result.success(Unit)
        }

    override suspend fun start(): Result<Unit> = transitionMutex.withLock {
        val source = _activeSource.value
            ?: return@withLock Result.failure(SourceError.Internal("No source attached"))
        try {
            source.start()
            _state.value = SessionState.Streaming(source.kind)
            Result.success(Unit)
        } catch (error: SourceError) {
            _state.value = SessionState.Failed(error)
            Result.failure(error)
        }
    }

    override suspend fun stop(): Result<Unit> = transitionMutex.withLock {
        val source = _activeSource.value
        if (source == null) {
            _state.value = SessionState.Stopped
            return@withLock Result.success(Unit)
        }
        return@withLock try {
            source.stop()
            _state.value = SessionState.Stopped
            Result.success(Unit)
        } catch (error: SourceError) {
            _state.value = SessionState.Failed(error)
            Result.failure(error)
        }
    }

    override fun playback(): PlaybackController? = _activeSource.value?.playbackController()

    /** Delivered frames per second measured over the last second. */
    fun measuredFps(): Double = rateMeter.fps()

    override fun close() {
        if (closed) return
        closed = true
        statsJob.cancel()
        val source = _activeSource.value
        _activeSource.value = null
        scope.launch {
            runCatching { source?.stop() }
            runCatching { source?.close() }
            _state.value = SessionState.Closed
            VcamLog.i(TAG, "Session closed")
            scope.cancel()
        }
    }

    private suspend fun releaseActiveSource() {
        val current = _activeSource.value ?: return
        _activeSource.value = null
        runCatching { current.stop() }
        runCatching { current.close() }
        runCatching { current.setFrameConsumer(null) }
    }

    companion object {
        private const val TAG = "Session"
    }
}
