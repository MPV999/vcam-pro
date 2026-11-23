package com.vcam.sdk.source

import com.vcam.sdk.api.CameraSource
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.SourceState
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.frames.FrameDispatcher
import com.vcam.sdk.frames.FrameStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared plumbing for the three source kinds: state machine, frame dispatcher with
 * drop-oldest backpressure, and the owned coroutine scope.
 *
 * A source owns its scope and cancels it on close; sources are single-use by contract.
 */
abstract class BaseCameraSource : CameraSource {

    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<SourceState>(SourceState.Idle)
    override val state: StateFlow<SourceState> = _state.asStateFlow()

    @Volatile
    private var dispatcher: FrameDispatcher? = null

    @Volatile
    private var consumer: FrameConsumer? = null

    protected var configuration: FrameConfiguration = FrameConfiguration.P720
        private set

    @Volatile
    override var sourceWidth: Int = 0

    @Volatile
    override var sourceHeight: Int = 0

    @Volatile
    override var durationUs: Long = 0L

    @Volatile
    protected var disposed: Boolean = false

    /** Starts a fresh dispatcher for a new open() and moves to [SourceState.Opening]. */
    protected fun beginSession(config: FrameConfiguration) {
        configuration = config
        dispatcher?.stop()
        dispatcher = FrameDispatcher(scope) { consumer }.also { it.start() }
        transition(SourceState.Opening)
    }

    protected fun endSession() {
        dispatcher?.stop()
        dispatcher = null
    }

    /** Hands a frame to the consumer pipeline. Takes ownership of [frame]. */
    protected fun emit(frame: Frame) {
        val current = dispatcher
        if (current == null) {
            frame.release()
        } else {
            current.submit(frame)
        }
    }

    protected fun transition(state: SourceState) {
        _state.value = state
    }

    protected fun failWith(error: SourceError) {
        _state.value = SourceState.Failed(error)
    }

    protected fun disposeScope() {
        scope.cancel()
    }

    /** Pipeline counters, for diagnostics screens and tests. */
    fun stats(): FrameStats = dispatcher?.stats() ?: FrameStats()

    override fun setFrameConsumer(consumer: FrameConsumer?) {
        this.consumer = consumer
        if (consumer == null) dispatcher?.clear()
    }
}
