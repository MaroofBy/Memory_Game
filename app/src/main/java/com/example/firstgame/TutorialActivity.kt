package com.example.firstgame

import android.os.Bundle
import android.widget.ImageButton // Corrected import
import pl.droidsonroids.gif.GifImageView
import androidx.appcompat.app.AppCompatActivity

class TutorialActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tutorial)

        val tutorialGif = findViewById<GifImageView>(R.id.tutorialGif)

        // CORRECTED: Use ImageButton instead of Button
        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // Resume the music when the activity becomes active
        MusicManager.resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        // Pause the music when the activity is sent to the background
        MusicManager.pauseMusic()
    }

}