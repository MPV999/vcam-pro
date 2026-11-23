package com.vcam.core.common

import java.util.Locale
import java.util.concurrent.TimeUnit

/** Human readable size, base 1024. */
fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "-"
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

/** mm:ss or h:mm:ss from milliseconds. */
fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "00:00"
    val hours = TimeUnit.MILLISECONDS.toHours(durationMs)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

/** Same as [formatDuration] but from microseconds, as media pipelines report. */
fun formatDurationUs(durationUs: Long): String = formatDuration(durationUs / 1000)

fun formatResolution(width: Int, height: Int): String = "${width}x${height}"

fun formatFps(fps: Double): String = String.format(Locale.US, "%.1f fps", fps)
