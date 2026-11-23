package com.vcam.core.common

import android.util.Log

/**
 * Thin logging facade. Every tag is prefixed so device logs stay searchable and
 * diagnostics can be filtered per subsystem.
 */
object VcamLog {

    private const val PREFIX = "VCAM/"

    @Volatile
    var enabled: Boolean = true

    fun d(subsystem: String, message: String) {
        if (enabled) Log.d(PREFIX + subsystem, message)
    }

    fun i(subsystem: String, message: String) {
        if (enabled) Log.i(PREFIX + subsystem, message)
    }

    fun w(subsystem: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(PREFIX + subsystem, message, throwable)
    }

    fun e(subsystem: String, message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(PREFIX + subsystem, message, throwable)
    }
}
