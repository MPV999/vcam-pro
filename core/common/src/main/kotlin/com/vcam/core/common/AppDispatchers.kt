package com.vcam.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatcher indirection so background work never runs on the main thread and can be
 * swapped in tests.
 */
interface AppDispatchers {
    val main: CoroutineDispatcher get() = Dispatchers.Main
    val io: CoroutineDispatcher get() = Dispatchers.IO
    val default: CoroutineDispatcher get() = Dispatchers.Default
}

object DefaultAppDispatchers : AppDispatchers
