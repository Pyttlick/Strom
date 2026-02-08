package com.example.strom.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

    // ===== OpenGL =====
    private var programId = 0
    private var mvpLocation = -1

    private var vboId = 0
    private var eboId = 0
    private var indexCount = 0

    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var indexBuffer: IntBuffer

    // ===== Matice =====
    private val modelMatrix = FloatArray(16)
    private val viewMatrix  = FloatArray(16)
    private val projMatrix  = FloatArray(16)
    private val mvpMatrix   = FloatArray(16)

    private var startTimeNs: Long = 0L

    // =========================================================
    // LIFECYCLE
    // =========================================================

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.05f, 0.05f, 0.15f, 1f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // =====================================================
        // SHADERY
        // =====================================================

        val vertexShaderCode = """
            #version 300 es
            layout(location = 0) in vec3 aPosition;
            layout(location = 1) in vec3 aColor;

            uniform mat4 uMVP;
            out vec3 vColor;

            void main() {
                gl_Position = uMVP * vec4(aPosition, 1.0);
                vColor = aColor;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            #version 300 es
            precision mediump float;

            in vec3 vColor;
            out vec4 fragColor;

            void main() {
                fragColor = vec4(vColor, 1.0);
            }
        """.trimIndent()

        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexShaderCode)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vs)
        GLES30.glAttachShader(programId, fs)
        GLES30.glLinkProgram(programId)

        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)

        mvpLocation = GLES30.glGetUniformLocation(programId, "uMVP")
        startTimeNs = System.nanoTime()

        // =====================================================
        // GENERACE STROMU – JABLOŇ
        // =====================================================

        val treeGenerator = TreeGenerator(
            segmentLength = 0.35f,
            segmentRadius = 0.05f,
            branchAngle = 25f
        )

        val axiom = "F"
        val rules = mapOf(
            // Řidší, asymetrická jabloň
            'F' to "F[+F][&F]"
        )

        val iterations = 5

        val (vBuf, iBuf) = treeGenerator.generate(
            axiom = axiom,
            rules = rules,
            iterations = iterations
        )

        vertexBuffer = vBuf
        indexBuffer = iBuf
        indexCount = indexBuffer.capacity()

        // =====================================================
        // VBO / EBO
        // =====================================================

        val buffers = IntArray(2)
        GLES30.glGenBuffers(2, buffers, 0)
        vboId = buffers[0]
        eboId = buffers[1]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertexBuffer.capacity() * 4,
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)
        GLES30.glBufferData(
            GLES30.GL_ELEMENT_ARRAY_BUFFER,
            indexBuffer.capacity() * 4,
            indexBuffer,
            GLES30.GL_STATIC_DRAW
        )

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)

        val ratio = width.toFloat() / height.toFloat()
        Matrix.perspectiveM(projMatrix, 0, 60f, ratio, 0.1f, 100f)

        Matrix.setLookAtM(
            viewMatrix, 0,
            0f, 0f, 4f,   // kamera
            0f, 0f, 0f,   // cíl
            0f, 1f, 0f
        )
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val time = (System.nanoTime() - startTimeNs) / 1_000_000_000f
        val angle = time * 20f

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, 0f, 0.8f, 0f) //ground
        Matrix.scaleM(modelMatrix, 0, 3.0f, 3.0f, 3.0f)
        Matrix.rotateM(modelMatrix, 0, angle, 0f, 1f, 0f)


        Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

        GLES30.glUseProgram(programId)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, eboId)

        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 6 * 4, 0)

        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 6 * 4, 3 * 4)

        GLES30.glUniformMatrix4fv(mvpLocation, 1, false, mvpMatrix, 0)

        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            indexCount,
            GLES30.GL_UNSIGNED_INT,
            0
        )

        GLES30.glDisableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
    }

    // =========================================================
    // SHADER UTILS
    // =========================================================

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            error("Shader compile error: $log")
        }
        return shader
    }
}
