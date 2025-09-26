package com.example.firstgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Movie
import android.util.AttributeSet
import android.view.View

class GifView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var movie: Movie? = null
    private var movieStart = 0L

    fun setGifResource(resourceId: Int) {
        val inputStream = context.resources.openRawResource(resourceId)
        movie = Movie.decodeStream(inputStream)
        movieStart = 0
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (movie == null) {
            super.onDraw(canvas)
            return
        }

        if (movieStart == 0L) {
            movieStart = System.currentTimeMillis()
        }

        val time = System.currentTimeMillis() - movieStart
        if (time > movie!!.duration()) {
            movieStart = System.currentTimeMillis()
        }

        movie!!.setTime((time % movie!!.duration()).toInt())
        movie!!.draw(canvas, 0f, 0f)

        invalidate() // Request another draw call to animate
    }
}