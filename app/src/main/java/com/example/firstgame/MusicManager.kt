package com.example.firstgame

import android.content.Context
import android.media.MediaPlayer

object MusicManager {
    private var mediaPlayer: MediaPlayer? = null
    var isMusicPlaying: Boolean = true // Tracks if music should be playing (user preference)

    fun startMusic(context: Context) {
        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(context, R.raw.background_music)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(0.5f, 0.5f) // Optional: Adjust volume
        }
        if (!mediaPlayer!!.isPlaying && isMusicPlaying) { // Only start if not already playing and user wants it
            mediaPlayer?.start()
        }
    }

    fun pauseMusic() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun resumeMusic() {
        if (mediaPlayer?.isPlaying == false && isMusicPlaying) { // Only resume if paused and user wants it
            mediaPlayer?.start()
        }
    }

    fun toggleMusic() {
        isMusicPlaying = !isMusicPlaying
        if (isMusicPlaying) {
            mediaPlayer?.start()
        } else {
            mediaPlayer?.pause()
        }
    }

    fun stopAndReleaseMusic() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        isMusicPlaying = true // Reset to default playing state for next app launch
    }
}