package com.example.strom.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.cos
import kotlin.math.sin
import android.opengl.Matrix

data class TurtleState(
    val position: FloatArray,
    val rotation: FloatArray,
    val length: Float,
    val radius: Float
)

class TreeGenerator(
    private val segmentLength: Float = 0.3f,
    private val segmentRadius: Float = 0.05f,
    private val branchAngle: Float = 25f,
) {

    private val vertices = mutableListOf<Float>()
    private val indices = mutableListOf<Int>()
    private val random = kotlin.random.Random(1234)

    fun generate(
        axiom: String,
        rules: Map<Char, String>,
        iterations: Int
    ): Pair<FloatBuffer, IntBuffer> {

        vertices.clear()
        indices.clear()

        val lsystemString = generateLSystem(axiom, rules, iterations)

        val stack = mutableListOf<TurtleState>()

        val position = floatArrayOf(0f, -1.0f, 0f)

        val rotation = FloatArray(16)
        Matrix.setIdentityM(rotation, 0)

        var currentLength = segmentLength
        var currentRadius = segmentRadius

        for (c in lsystemString) {
            when (c) {

                'F' -> {
                    val color = floatArrayOf(0.4f, 0.7f, 0.3f)

                    addCylinder(
                        startPos = position,
                        rotation = rotation,
                        length = currentLength,
                        radius = currentRadius,
                        sides = 8,
                        color = color
                    )

                    // posun turtle vpřed (lokální Y osa)
                    position[0] += currentLength * rotation[4]
                    position[1] += currentLength * rotation[5]
                    position[2] += currentLength * rotation[6]
                }

                '+' -> {
                    val rot = FloatArray(16)
                    val jitter = random.nextFloat() * 10f - 5f
                    Matrix.setRotateM(rot, 0, branchAngle + jitter, 0f, 0f, 1f)
                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, rotation, 0, rot, 0)
                    System.arraycopy(tmp, 0, rotation, 0, 16)
                }

                '-' -> {
                    val rot = FloatArray(16)
                    val jitter = random.nextFloat() * 10f - 5f
                    Matrix.setRotateM(rot, 0, -(branchAngle + jitter), 0f, 0f, 1f)
                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, rotation, 0, rot, 0)
                    System.arraycopy(tmp, 0, rotation, 0, 16)
                }

                '&' -> {
                    val rot = FloatArray(16)
                    val jitter = random.nextFloat() * 10f - 5f
                    Matrix.setRotateM(rot, 0, branchAngle + jitter, 1f, 0f, 0f)
                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, rotation, 0, rot, 0)
                    System.arraycopy(tmp, 0, rotation, 0, 16)
                }

                '^' -> {
                    val rot = FloatArray(16)
                    val jitter = random.nextFloat() * 10f - 5f
                    Matrix.setRotateM(rot, 0, -(branchAngle + jitter), 1f, 0f, 0f)
                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, rotation, 0, rot, 0)
                    System.arraycopy(tmp, 0, rotation, 0, 16)
                }

                '[' -> {
                    // náhodné pootočení kolem Y (rozhození do prostoru)
                    val yaw = FloatArray(16)
                    Matrix.setRotateM(
                        yaw,
                        0,
                        random.nextFloat() * 360f,
                        0f, 1f, 0f
                    )

                    val tmp = FloatArray(16)
                    Matrix.multiplyMM(tmp, 0, rotation, 0, yaw, 0)
                    System.arraycopy(tmp, 0, rotation, 0, 16)

                    stack.add(
                        TurtleState(
                            position.copyOf(),
                            rotation.copyOf(),
                            currentLength,
                            currentRadius
                        )
                    )
                    currentLength *= 0.8f
                    currentRadius *= 0.7f
                }

                ']' -> {
                    val s = stack.removeAt(stack.lastIndex)
                    System.arraycopy(s.position, 0, position, 0, 3)
                    System.arraycopy(s.rotation, 0, rotation, 0, 16)
                    currentLength = s.length
                    currentRadius = s.radius
                }
            }
        }

        val vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertices.toFloatArray())
        vertexBuffer.position(0)

        val indexBuffer = ByteBuffer.allocateDirect(indices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asIntBuffer()
            .put(indices.toIntArray())
        indexBuffer.position(0)

        return Pair(vertexBuffer, indexBuffer)
    }

    private fun generateLSystem(
        axiom: String,
        rules: Map<Char, String>,
        iterations: Int
    ): String {
        var current = axiom
        repeat(iterations) {
            val sb = StringBuilder()
            for (c in current) {
                sb.append(rules.getOrDefault(c, c.toString()))
            }
            current = sb.toString()
        }
        return current
    }

    private fun addCylinder(
        startPos: FloatArray,
        rotation: FloatArray,
        length: Float,
        radius: Float,
        sides: Int,
        color: FloatArray
    ) {
        val angleStep = (2.0 * Math.PI / sides).toFloat()
        val baseIndex = vertices.size / 6

        for (i in 0 until sides) {
            val angle = i * angleStep
            val x = cos(angle) * radius
            val z = sin(angle) * radius

            addVertexTransformed(startPos, rotation, x, 0f, z, color)
            addVertexTransformed(startPos, rotation, x, length, z, color)
        }

        for (i in 0 until sides) {
            val next = (i + 1) % sides

            val b0 = baseIndex + i * 2
            val t0 = b0 + 1
            val b1 = baseIndex + next * 2
            val t1 = b1 + 1

            indices.add(b0)
            indices.add(t0)
            indices.add(b1)

            indices.add(t0)
            indices.add(t1)
            indices.add(b1)
        }
    }

    private fun addVertexTransformed(
        basePos: FloatArray,
        rot: FloatArray,
        lx: Float,
        ly: Float,
        lz: Float,
        color: FloatArray
    ) {
        val v = floatArrayOf(lx, ly, lz, 1f)
        val out = FloatArray(4)
        Matrix.multiplyMV(out, 0, rot, 0, v, 0)

        vertices.add(basePos[0] + out[0])
        vertices.add(basePos[1] + out[1])
        vertices.add(basePos[2] + out[2])

        vertices.add(color[0])
        vertices.add(color[1])
        vertices.add(color[2])
    }
}
