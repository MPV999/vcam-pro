package com.vcam.feature.playback

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Media3 based preview used by the Virtual Camera Studio to show the source before it is
 * handed to the SDK.
 *
 * This is a *preview* only: the frames the SDK delivers come from the SDK's own MediaCodec
 * pipeline, not from this player.
 */
@Composable
fun VideoPreview(
    uri: Uri?,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
    muted: Boolean = true,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            player.stop()
            player.release()
        }
    }

    LaunchedEffect(uri, looping, muted) {
        player.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        player.volume = if (muted) 0f else 1f
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.clearMediaItems()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view -> view.player = player },
    )
}
