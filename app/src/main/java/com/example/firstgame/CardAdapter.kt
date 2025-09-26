package com.example.firstgame

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView

class CardAdapter(
    private val context: Context,
    private var images: MutableList<Int>,
    private val cardSize: Int = 80,
    private val lockedPositions: Set<Int>,
    private var isKeyFound: Boolean
) : BaseAdapter() {

    private val revealed = MutableList(images.size) { false }
    private val fogRevealed = mutableSetOf<Int>()
    private var isFogLevel = false
    private var matchCount = 0  // Add this variable


    override fun getCount(): Int = images.size
    override fun getItem(position: Int): Any = images[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_card, parent, false)
        val imageView: ImageView = view.findViewById(R.id.cardImage)
        val lockIcon: ImageView = view.findViewById(R.id.lockIcon) // Get the lock icon


        // Convert dp to pixels for layout parameters
        val density = context.resources.displayMetrics.density
        val sizeInPixels = (cardSize * density).toInt()

        // Set dynamic size
        val layoutParams = imageView.layoutParams
        layoutParams.width = sizeInPixels
        layoutParams.height = sizeInPixels
        imageView.layoutParams = layoutParams

        if (revealed[position]) {
            // Card is face-up: show the image, hide the lock
            imageView.setImageResource(images[position])
            lockIcon.visibility = View.GONE
        } else {
            // Card is face-down: always show the card back
            imageView.setImageResource(R.drawable.card_back)

            // Now, decide if we should show the lock on top of it
            if (position in lockedPositions && !isKeyFound) {
                lockIcon.visibility = View.VISIBLE // Show the lock
            } else {
                lockIcon.visibility = View.GONE // Hide the lock
            }
        }


        if (isFogLevel && position !in fogRevealed) {
            Log.d("FogOfWar", "Showing fog for position $position")
            imageView.setImageResource(R.drawable.card_back)
            imageView.alpha = 0.0f
            return view
        }

        imageView.alpha = 1.0f  // Full visibility for revealed cards
        if (revealed[position]) {
            imageView.setImageResource(images[position])
        } else {
            imageView.setImageResource(R.drawable.card_back)
        }
        if (position in lockedPositions) {
            if (!isKeyFound) {
                lockIcon.visibility = View.VISIBLE
                lockIcon.alpha = 1f // Make sure it's fully visible
            } else {
                if (lockIcon.visibility == View.VISIBLE) {
                    lockIcon.animate()
                        .alpha(0f)
                        .setDuration(500) // Fades out over 0.5 seconds
                        .withEndAction {
                            lockIcon.visibility = View.GONE
                        }
                        .start()
                }
            }
        } else {
            // State 3: Card is not a locked card
            lockIcon.visibility = View.GONE
        }


        return view

    }
    fun onMatchMade() {
        if (isFogLevel) {
            matchCount++
            if (matchCount % 3 == 0) {
                revealMoreCards()
            }
        }
    }

    private fun revealMoreCards() {
        val unrevealed = (0 until images.size).filter { it !in fogRevealed }
        val imageToPositions = mutableMapOf<Int, MutableList<Int>>()

        // Group unrevealed positions by image
        for (pos in unrevealed) {
            val imageId = images[pos]
            imageToPositions.getOrPut(imageId) { mutableListOf() }.add(pos)
        }

        val newReveals = mutableSetOf<Int>()

        // First: try to reveal complete pairs
        for ((imageId, positions) in imageToPositions) {
            if (positions.size == 2 && newReveals.size + 2 <= 6) {
                newReveals.addAll(positions)
            }
        }

        // Second: if we need more cards, add sequential ones
        if (newReveals.size < 6) {
            val remaining = 6 - newReveals.size
            unrevealed.take(remaining + 10) // Take more to find unused ones
                .filter { it !in newReveals }
                .take(remaining)
                .forEach { newReveals.add(it) }
        }

        fogRevealed.addAll(newReveals)
        notifyDataSetChanged()
    }

    fun isPositionRevealed(position: Int): Boolean {
        if (!isFogLevel) return true
        return position in fogRevealed
    }

    fun isCardRevealed(position: Int): Boolean {
        return revealed[position]
    }
    fun revealCard(position: Int) {
        revealed[position] = true
        notifyDataSetChanged()
    }

    fun hideCard(position: Int) {
        revealed[position] = false
        notifyDataSetChanged()
    }
    fun enableFogOfWar() {
        isFogLevel = true
        fogRevealed.clear()

        // Find complete pairs and reveal them together
        val pairedPositions = mutableSetOf<Int>()
        val imageToPositions = mutableMapOf<Int, MutableList<Int>>()

        // Group positions by image
        for (i in images.indices) {
            val imageId = images[i]
            imageToPositions.getOrPut(imageId) { mutableListOf() }.add(i)
        }

        // Add complete pairs until we have ~20 cards
        for ((imageId, positions) in imageToPositions) {
            if (positions.size == 2 && pairedPositions.size + 2 <= 20) {
                pairedPositions.addAll(positions)
            }
            if (pairedPositions.size >= 18) break // Stop at 18-20 cards
        }

        fogRevealed.addAll(pairedPositions)
        notifyDataSetChanged()
    }
    fun updateImagesAndRevealedStates(newImages: List<Int>, revealedStates: List<Boolean>) {
        // Don't try to modify the original images list
        // Instead, create a new internal mutable copy
        this.images = newImages.toMutableList()

        // Update revealed states
        for (i in revealedStates.indices) {
            revealed[i] = revealedStates[i]
        }

        notifyDataSetChanged()
    }
    fun updateImages(newImages: List<Int>) {
        this.images = newImages.toMutableList()
        notifyDataSetChanged()
    }
    fun unlockAllCards() {
        this.isKeyFound = true
        notifyDataSetChanged() // This forces the grid to redraw, removing the locks
    }
}


