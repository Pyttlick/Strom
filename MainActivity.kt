package com.example.strom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.strom.gl.GLView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(GLView(this))
    }
}
