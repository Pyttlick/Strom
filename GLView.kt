
package com.example.strom.gl

import android.content.Context
import android.opengl.GLSurfaceView

class GLView(context: Context) : GLSurfaceView(context) {

    private val renderer: GLRenderer

    init {
        // Explicitně říkáme: chceme OpenGL ES 3.0
        setEGLContextClientVersion(3)

        renderer = GLRenderer()
        setRenderer(renderer)

        // Budeme renderovat pořád (rotace objektu)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
