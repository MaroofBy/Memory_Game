package com.example.firstgame

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show splash screen for 2 seconds before going to MainSplashActivity
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainSplashActivity::class.java))
            finish()
        }, 1500) // 2000ms = 2 seconds
    }
}
