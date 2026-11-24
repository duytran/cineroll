package app.cineroll.develop

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES30
import android.opengl.GLUtils
import app.cineroll.film.Film
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * One offscreen GLES 3.0 pass that applies a Film to a bitmap. A fresh EGL context is created and
 * torn down per call, so it is safe to call from any background thread.
 *
 * The shader mirrors `FilmMath` (gains, saturation, contrast, lift) and adds grain: a hash of the
 * pixel coordinates, so the same image and Film always give the same output.
 */
internal class FilmRenderer {

    fun render(source: Bitmap, film: Film): Bitmap {
        val width = source.width
        val height = source.height

        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        if (display == EGL14.EGL_NO_DISPLAY || !EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw DevelopException("EGL is not available")
        }

        var context: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        var program = 0
        val textures = IntArray(1)
        try {
            val config = chooseConfig(display)
            context = EGL14.eglCreateContext(
                display, config, EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE), 0,
            )
            if (context == EGL14.EGL_NO_CONTEXT) throw DevelopException("Could not create a GL context")
            surface = EGL14.eglCreatePbufferSurface(
                display, config,
                intArrayOf(EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE), 0,
            )
            if (surface == EGL14.EGL_NO_SURFACE) throw DevelopException("Image too large for the GPU: ${width}x$height")
            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
                throw DevelopException("Could not use the GL context")
            }

            val maxTexture = IntArray(1)
            GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maxTexture, 0)
            if (width > maxTexture[0] || height > maxTexture[0]) {
                throw DevelopException("Image too large for the GPU: ${width}x$height")
            }

            program = buildProgram()
            GLES30.glGenTextures(1, textures, 0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, source, 0)

            GLES30.glViewport(0, 0, width, height)
            GLES30.glUseProgram(program)
            GLES30.glUniform1i(GLES30.glGetUniformLocation(program, "uImage"), 0)
            GLES30.glUniform3f(GLES30.glGetUniformLocation(program, "uGain"), film.redGain, film.greenGain, film.blueGain)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uSaturation"), film.saturation)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uContrast"), film.contrast)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uLift"), film.lift)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(program, "uGrain"), film.grain)

            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 0, QUAD)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glFinish()
            checkGl("draw")

            // The quad maps framebuffer row 0 to image row 0, so rows come back top-down: no flip needed.
            val pixels = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels)
            checkGl("read")
            pixels.rewind()
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            result.copyPixelsFromBuffer(pixels)
            return result
        } finally {
            if (textures[0] != 0) GLES30.glDeleteTextures(1, textures, 0)
            if (program != 0) GLES30.glDeleteProgram(program)
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context)
        }
    }

    private fun chooseConfig(display: EGLDisplay): EGLConfig {
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) {
            throw DevelopException("No suitable GL config")
        }
        return configs[0]!!
    }

    private fun buildProgram(): Int {
        val vertex = compile(GLES30.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compile(GLES30.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertex)
        GLES30.glAttachShader(program, fragment)
        GLES30.glLinkProgram(program)
        GLES30.glDeleteShader(vertex)
        GLES30.glDeleteShader(fragment)
        val status = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw DevelopException("Could not link the Film shader: $log")
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw DevelopException("Could not compile the Film shader: $log")
        }
        return shader
    }

    private fun checkGl(step: String) {
        val error = GLES30.glGetError()
        if (error != GLES30.GL_NO_ERROR) throw DevelopException("GL error 0x${error.toString(16)} after $step")
    }

    private companion object {
        val QUAD: FloatBuffer = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

        const val VERTEX_SHADER = """#version 300 es
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.0, 1.0);
}
"""

        const val FRAGMENT_SHADER = """#version 300 es
precision highp float;
uniform sampler2D uImage;
uniform vec3 uGain;
uniform float uSaturation;
uniform float uContrast;
uniform float uLift;
uniform float uGrain;
in vec2 vUv;
out vec4 outColor;

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

void main() {
    vec3 c = texture(uImage, vUv).rgb * uGain;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = vec3(luma) + (c - vec3(luma)) * uSaturation;
    c = (c - 0.5) * uContrast + 0.5;
    c = uLift + c * (1.0 - uLift);
    c += (hash(gl_FragCoord.xy) - 0.5) * uGrain;
    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""
    }
}
