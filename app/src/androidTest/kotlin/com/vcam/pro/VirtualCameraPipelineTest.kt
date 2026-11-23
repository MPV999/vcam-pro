package com.vcam.pro

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vcam.sdk.api.SessionState
import com.vcam.sdk.api.SourceError
import com.vcam.sdk.api.VirtualCameraConfig
import com.vcam.sdk.format.FrameConfiguration
import com.vcam.sdk.format.FrameFormat
import com.vcam.sdk.frames.Frame
import com.vcam.sdk.frames.FrameConsumer
import com.vcam.sdk.source.VirtualCameraSdk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Instrumented acceptance test for the SDK pipeline. Requires a device or emulator.
 *
 * The image-source test proves the whole chain end to end without needing a camera or a
 * LifecycleOwner: file -> Bitmap -> GL -> read-back -> Frame -> consumer.
 */
@RunWith(AndroidJUnit4::class)
class VirtualCameraPipelineTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun writeTestImage(name: String, color: Int): File {
        val file = File(context.filesDir, name)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        bitmap.recycle()
        return file
    }

    @Test
    fun sessionStartsIdle() = runBlocking {
        val session = VirtualCameraSdk.createSession()
        assertEquals(SessionState.Idle, session.state.value)
        session.close()
    }

    @Test
    fun missingFileFailsWithTypedError() = runBlocking {
        val session = VirtualCameraSdk.createSession()
        val source = VirtualCameraSdk.videoSource(
            context,
            Uri.fromFile(File("/nonexistent/clip.mp4")),
        )
        val result = session.useSource(source)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SourceError)
        assertTrue(session.state.value is SessionState.Failed)
        session.close()
    }

    @Test
    fun closeIsIdempotent() {
        val session = VirtualCameraSdk.createSession()
        session.close()
        session.close()
        assertEquals(SessionState.Closed, session.state.value)
    }

    @Test
    fun imageSourceDeliversContinuousFrames() = runBlocking {
        val file = writeTestImage("pipeline_test_frame.png", Color.RED)

        val delivered = AtomicInteger(0)
        val firstFrameFormat = arrayOfNulls<FrameFormat>(1)
        val session = VirtualCameraSdk.createSession(
            config = VirtualCameraConfig(
                configuration = FrameConfiguration(320, 240, 30, FrameFormat.RGBA_8888),
            ),
            consumer = FrameConsumer { frame: Frame ->
                if (firstFrameFormat[0] == null) firstFrameFormat[0] = frame.format
                delivered.incrementAndGet()
                frame.release()
            },
        )

        val result = session.useSource(VirtualCameraSdk.imageSource(context, Uri.fromFile(file)))
        assertTrue("image source must open: ${result.exceptionOrNull()?.message}", result.isSuccess)

        delay(1_500)
        session.close()
        file.delete()

        assertTrue("expected a continuous stream, got ${delivered.get()}", delivered.get() > 10)
        assertEquals(FrameFormat.RGBA_8888, firstFrameFormat[0])
    }

    @Test
    fun switchingSourcesRepeatedlyDoesNotCrash() = runBlocking {
        val file = writeTestImage("switch_test_frame.png", Color.BLUE)
        val session = VirtualCameraSdk.createSession()

        repeat(5) {
            session.useSource(VirtualCameraSdk.imageSource(context, Uri.fromFile(file)))
            delay(120)
            session.useSource(VirtualCameraSdk.imageSource(context, Uri.fromFile(file)))
            delay(120)
        }

        session.close()
        file.delete()
        assertEquals(SessionState.Closed, session.state.value)
    }
}
