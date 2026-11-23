package com.vcam.sdk.playback

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface

/**
 * Minimal EGL 2.0 environment with a 1x1 pbuffer.
 *
 * We render into a framebuffer object and read the pixels back, so nothing is ever shown
 * on screen; a pbuffer keeps the context valid without a window surface.
 *
 * The instance is bound to the thread that called [initialize]: EGL contexts are
 * thread-affine, so every draw and every `updateTexImage` must happen on that thread.
 */
internal class EglCore {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE

    val isInitialized: Boolean get() = display != EGL14.EGL_NO_DISPLAY

    fun initialize() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(display != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }

        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1)) { "eglInitialize failed" }

        val configAttributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(display, configAttributes, 0, configs, 0, 1, numConfigs, 0) &&
                numConfigs[0] > 0,
        ) { "no suitable EGL config" }

        val config = configs[0] ?: error("no EGL config")

        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE,
        )
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
        checkEglError("eglCreateContext")
        check(context != EGL14.EGL_NO_CONTEXT) { "null EGL context" }

        val surfaceAttributes = intArrayOf(
            EGL14.EGL_WIDTH, 1,
            EGL14.EGL_HEIGHT, 1,
            EGL14.EGL_NONE,
        )
        surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttributes, 0)
        checkEglError("eglCreatePbufferSurface")
        check(surface != EGL14.EGL_NO_SURFACE) { "null EGL surface" }

        check(EGL14.eglMakeCurrent(display, surface, surface, context)) { "eglMakeCurrent failed" }
    }

    fun release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
            EGL14.eglTerminate(display)
        }
        display = EGL14.EGL_NO_DISPLAY
        context = EGL14.EGL_NO_CONTEXT
        surface = EGL14.EGL_NO_SURFACE
    }

    private fun checkEglError(operation: String) {
        val error = EGL14.eglGetError()
        check(error == EGL14.EGL_SUCCESS) { "$operation failed: 0x${Integer.toHexString(error)}" }
    }
}
