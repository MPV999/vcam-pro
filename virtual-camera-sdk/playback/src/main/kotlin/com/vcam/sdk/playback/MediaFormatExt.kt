package com.vcam.sdk.playback

import android.media.MediaFormat

/** MediaFormat accessors that never throw on missing keys. */
internal fun MediaFormat.optInt(key: String, fallback: Int): Int =
    if (containsKey(key)) getInteger(key) else fallback

internal fun MediaFormat.optLong(key: String, fallback: Long): Long =
    if (containsKey(key)) getLong(key) else fallback

internal fun MediaFormat.optStringOrNull(key: String): String? =
    if (containsKey(key)) getString(key) else null
