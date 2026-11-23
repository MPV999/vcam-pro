package com.vcam.sdk.playback

import android.graphics.Bitmap
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import com.vcam.sdk.format.ScaleMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renders a source (external OES texture from MediaCodec, or a 2D texture from a Bitmap)
 * into an off-screen framebuffer at the output size, honouring [ScaleMode], then reads the
 * result back as top-down RGBA.
 *
 * Read-back is not free: `glReadPixels` at 720p30 moves ~110 MB/s. That is the price of
 * handing real pixel data to an arbitrary consumer without a shared surface, and it is
 * why the SDK lets consumers pick a smaller output resolution.
 *
 * Thread affine: use only on the thread where [initialize] was called.
 */
internal class GlFrameCompositor {

    private val egl = EglCore()

    private var program2D = 0
    private var programOes = 0
    private var framebuffer = 0
    private var framebufferTexture = 0

    private var outputWidth = 0
    private var outputHeight = 0

    private val texMatrixBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(16 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    private val vertexBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(VERTEX_FLOATS * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun initialize() {
        egl.initialize()
        program2D = buildProgram(useExternalTexture = false)
        programOes = buildProgram(useExternalTexture = true)
    }

    fun setOutputSize(width: Int, height: Int) {
        if (width == outputWidth && height == outputHeight && framebuffer != 0) return
        check(width > 0 && height > 0) { "invalid output size ${width}x$height" }

        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            GLES20.glDeleteTextures(1, intArrayOf(framebufferTexture), 0)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        framebufferTexture = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, framebufferTexture)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        val buffers = IntArray(1)
        GLES20.glGenFramebuffers(1, buffers, 0)
        framebuffer = buffers[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
            GLES20.GL_TEXTURE_2D, framebufferTexture, 0,
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "framebuffer incomplete: 0x${Integer.toHexString(status)}" }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

        outputWidth = width
        outputHeight = height
    }

    /** Creates a texture usable by `SurfaceTexture` for MediaCodec output. */
    fun createExternalTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        return id
    }

    fun createImageTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val id = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return id
    }

    fun uploadBitmap(textureId: Int, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun drawExternal(
        textureId: Int,
        texMatrix: FloatArray,
        sourceWidth: Int,
        sourceHeight: Int,
        scaleMode: ScaleMode,
    ) {
        texMatrixBuffer.clear()
        texMatrixBuffer.put(texMatrix).position(0)
        draw(programOes, textureId, GLES11Ext.GL_TEXTURE_EXTERNAL_OES, sourceWidth, sourceHeight, scaleMode, flipY = 1f)
    }

    fun draw2D(
        textureId: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        scaleMode: ScaleMode,
    ) {
        texMatrixBuffer.clear()
        texMatrixBuffer.put(IDENTITY_MATRIX).position(0)
        draw(program2D, textureId, GLES20.GL_TEXTURE_2D, sourceWidth, sourceHeight, scaleMode, flipY = 0f)
    }

    private fun draw(
        program: Int,
        textureId: Int,
        textureTarget: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        scaleMode: ScaleMode,
        flipY: Float,
    ) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val scaleX: Float
        val scaleY: Float
        when (scaleMode) {
            ScaleMode.FIT -> {
                val scale = minOf(
                    outputWidth.toFloat() / sourceWidth,
                    outputHeight.toFloat() / sourceHeight,
                )
                scaleX = sourceWidth * scale / outputWidth
                scaleY = sourceHeight * scale / outputHeight
            }
            ScaleMode.CROP -> {
                val scale = maxOf(
                    outputWidth.toFloat() / sourceWidth,
                    outputHeight.toFloat() / sourceHeight,
                )
                scaleX = sourceWidth * scale / outputWidth
                scaleY = sourceHeight * scale / outputHeight
            }
            ScaleMode.FILL -> {
                scaleX = 1f
                scaleY = 1f
            }
        }

        // Texture v=0 is placed at the bottom of the framebuffer so that a subsequent
        // glReadPixels (which returns rows bottom-up) yields top-down image rows.
        vertexBuffer.clear()
        vertexBuffer.put(
            floatArrayOf(
                -scaleX, -scaleY, 0f, 0f,
                scaleX, -scaleY, 1f, 0f,
                -scaleX, scaleY, 0f, 1f,
                scaleX, scaleY, 1f, 1f,
            ),
        ).position(0)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        GLES20.glUniformMatrix4fv(
            GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrixBuffer,
        )
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uFlipY"), flipY)

        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(ATTRIB_POSITION, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(ATTRIB_POSITION)
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(ATTRIB_TEX_COORD, 2, GLES20.GL_FLOAT, false, 16, vertexBuffer)
        GLES20.glEnableVertexAttribArray(ATTRIB_TEX_COORD)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(ATTRIB_POSITION)
        GLES20.glDisableVertexAttribArray(ATTRIB_TEX_COORD)
        GLES20.glBindTexture(textureTarget, 0)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    /** Reads the last drawn frame as top-down RGBA into [destination]. */
    fun readPixels(destination: ByteBuffer) {
        destination.clear()
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer)
        GLES20.glReadPixels(
            0, 0, outputWidth, outputHeight,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, destination,
        )
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        destination.position(0)
        destination.limit(outputWidth * outputHeight * 4)
    }

    fun release() {
        if (framebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, intArrayOf(framebuffer), 0)
            framebuffer = 0
        }
        if (framebufferTexture != 0) {
            GLES20.glDeleteTextures(1, intArrayOf(framebufferTexture), 0)
            framebufferTexture = 0
        }
        if (program2D != 0) {
            GLES20.glDeleteProgram(program2D)
            program2D = 0
        }
        if (programOes != 0) {
            GLES20.glDeleteProgram(programOes)
            programOes = 0
        }
        egl.release()
        outputWidth = 0
        outputHeight = 0
    }

    private fun buildProgram(useExternalTexture: Boolean): Int {
        val vertexSource = """
            attribute vec4 aPosition;
            attribute vec4 aTexCoord;
            uniform mat4 uTexMatrix;
            uniform float uFlipY;
            varying vec2 vTexCoord;
            void main() {
                vec4 transformed = uTexMatrix * aTexCoord;
                float y = mix(transformed.y, 1.0 - transformed.y, uFlipY);
                vTexCoord = vec2(transformed.x, y);
                gl_Position = aPosition;
            }
        """.trimIndent()

        val fragmentSource = if (useExternalTexture) {
            """
                #extension GL_OES_EGL_image_external : require
                precision mediump float;
                varying vec2 vTexCoord;
                uniform samplerExternalOES uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()
        } else {
            """
                precision mediump float;
                varying vec2 vTexCoord;
                uniform sampler2D uTexture;
                void main() {
                    gl_FragColor = texture2D(uTexture, vTexCoord);
                }
            """.trimIndent()
        }

        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glBindAttribLocation(program, ATTRIB_POSITION, "aPosition")
        GLES20.glBindAttribLocation(program, ATTRIB_TEX_COORD, "aTexCoord")
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            error("program link failed: $log")
        }
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("shader compile failed: $log")
        }
        return shader
    }

    companion object {
        private const val ATTRIB_POSITION = 0
        private const val ATTRIB_TEX_COORD = 1
        private const val VERTEX_FLOATS = 16

        private val IDENTITY_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
    }
}
