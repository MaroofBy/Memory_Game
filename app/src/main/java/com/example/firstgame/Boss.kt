package com.example.firstgame

// --- Imports ---
import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat

/**
 * A sealed class to define the specific boss types.
 */
sealed class Boss(val level: Int, val bossName: String) {
    object Locksmith : Boss(10, "LOCKSMITH")
    object Trickster : Boss(20, "TRICKSTER")
    object Lynx : Boss(30, "LYNX")
    object Shadow : Boss(36, "SHADOW")

    companion object {
        fun fromLevel(level: Int): Boss? {
            return when (level) {
                Locksmith.level -> Locksmith
                Trickster.level -> Trickster
                Lynx.level -> Lynx
                Shadow.level -> Shadow
                else -> null
            }
        }
    }
}

/**
 * This class manages all the logic and state for a boss level.
 */
class BossManager(
    private val activity: GameActivity,
    private val boss: Boss?
) {

    // --- State Properties for Bosses ---
    private var isKeyFound = false
    private val lockedCardPositions = mutableSetOf<Int>()
    private val keyImageResource = R.drawable.ic_key

    private var shuffleTimer: CountDownTimer? = null
    private val SHUFFLE_INTERVAL = 15000L
    private val SHUFFLE_WARNING_TIME = 3000L

    private var bombTimer: CountDownTimer? = null
    private var isBombTicking = false
    private val bombImageResource = R.drawable.ic_bomb

    fun setupLevel(levelTextView: TextView, originalImages: List<Int>, numPairs: Int): MutableList<Int> {
        when (boss) {
            Boss.Locksmith -> {
                levelTextView.text = boss.bossName
                levelTextView.setTextColor(Color.YELLOW)
                levelTextView.textSize = 35f
                levelTextView.typeface = ResourcesCompat.getFont(activity, R.font.carnival)
                val otherImages = originalImages.filter { it != keyImageResource }.shuffled().take(numPairs - 1)
                val finalUniqueImages = otherImages + keyImageResource
                val imagePairs = finalUniqueImages + finalUniqueImages
                val newImages = imagePairs.shuffled().toMutableList()
                selectLockedCards(newImages, 6)
                return newImages
            }
            Boss.Trickster -> {
                levelTextView.text = boss.bossName
                levelTextView.setTextColor(Color.parseColor("#40E0D0"))
                levelTextView.textSize = 35f
                levelTextView.typeface = ResourcesCompat.getFont(activity, R.font.carnival)
            }
            Boss.Lynx -> {
                levelTextView.text = boss.bossName
                levelTextView.setTextColor(Color.parseColor("#DC143C"))
                levelTextView.textSize = 50f
                levelTextView.typeface = ResourcesCompat.getFont(activity, R.font.carnival)
                val otherImages = originalImages.filter { it != bombImageResource }.shuffled().take(numPairs - 1)
                val finalUniqueImages = otherImages + bombImageResource
                val imagePairs = finalUniqueImages + finalUniqueImages
                return imagePairs.shuffled().toMutableList()
            }
            // In BossManager.kt
            Boss.Shadow -> {
                levelTextView.text = boss.bossName
                levelTextView.setTextColor(Color.RED)
                levelTextView.textSize = 40f
                levelTextView.typeface = ResourcesCompat.getFont(activity, R.font.carnival)
                // The incorrect lines have been removed.
            }
            null -> {}
        }
        val selectedUniqueImages = originalImages.shuffled().take(numPairs)
        val imagePairs = selectedUniqueImages + selectedUniqueImages
        return imagePairs.shuffled().toMutableList()
    }

    fun onCardClick(position: Int): Boolean {
        return when (boss) {
            Boss.Locksmith -> {
                if (!isKeyFound && position in lockedCardPositions) {
                    Toast.makeText(activity, "Find the key pair to unlock these cards!", Toast.LENGTH_SHORT).show()
                    true
                } else {
                    false
                }
            }
            Boss.Shadow -> {
                (activity.findViewById<GridView>(R.id.gridView).adapter as CardAdapter).let {
                    !it.isPositionRevealed(position)
                }
            }
            else -> false
        }
    }

    fun onCardFlipped(position: Int, images: List<Int>) {
        if (boss == Boss.Lynx && !isBombTicking && images[position] == bombImageResource) {
            startBombTimer()
        }
    }

    fun onMatchFound(pos1: Int, images: List<Int>) {
        val adapter = activity.findViewById<GridView>(R.id.gridView).adapter as CardAdapter
        when (boss) {
            Boss.Locksmith -> {
                if (!isKeyFound && images[pos1] == keyImageResource) {
                    isKeyFound = true
                    showCardsUnlockedMessage()
                    adapter.unlockAllCards()
                }
            }
            Boss.Lynx -> {
                if (isBombTicking && images[pos1] == bombImageResource) {
                    defuseBomb()
                }
            }
            Boss.Shadow -> {
                adapter.onMatchMade()
            }
            else -> {}
        }
    }

    fun showTutorialAndStartTimers() {
        val tutorialData = when (boss) {
            Boss.Locksmith -> Triple("LOCKSMITH: Locked Cards! 🔒", "Some cards are locked! You must find the key pair first to unlock the rest of the board.", "seen_level_10_lock_tutorial")
            Boss.Trickster -> Triple("TRICKSTER: Watch Out!", "The cards on this level will automatically shuffle every 15 seconds! Only hidden and unmatched cards will move.", "seen_level_20_tutorial")
            Boss.Lynx -> Triple("LYNX: Defuse the Bomb! 💣", "This level has a hidden bomb pair! If you flip the first one, a 15-second timer starts. Find the second bomb to defuse it, or you'll face a heavy penalty!", "seen_level_30_bomb_tutorial")
            Boss.Shadow -> Triple("SHADOW: Fog of War", "The board is hidden in fog! For every 3 pairs you match, a new set of cards will be revealed.", "seen_level_36_tutorial")
            else -> null
        }

        if (tutorialData != null) {
            val (title, message, prefKey) = tutorialData
            showTutorialIfNeeded(prefKey, title, message)
        } else {
            activity.startGameTimers()
        }
    }

    private fun showTutorialIfNeeded(prefKey: String, title: String, message: String) {
        val sharedPreferences = activity.getSharedPreferences("game_tutorials", Context.MODE_PRIVATE)
        if (!sharedPreferences.getBoolean(prefKey, false)) {
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got It!") { dialog, _ ->
                    sharedPreferences.edit().putBoolean(prefKey, true).apply()
                    activity.startGameTimers()
                    dialog.dismiss()
                }
                .setCancelable(false)
                .show()
        } else {
            activity.startGameTimers()
        }
    }

    fun customizeWinDialog(title: TextView, message: TextView, playAgainBtn: Button, exitBtn: Button, trophy: ImageView): Boolean {
        if (boss == null) return false

        title.text = "${boss.bossName} DEFEATED!"
        trophy.setImageResource(R.drawable.ic_award)
        when (boss) {
            Boss.Locksmith -> message.text = "A master of keys! Your sharp mind has unlocked the path forward."
            Boss.Trickster -> message.text = "Congratulations! You outsmarted the chaotic shuffle."
            Boss.Lynx -> message.text = "Congratulations! You kept your cool under pressure."
            Boss.Shadow -> {
                message.text = "Congratulations! You've conquered the ultimate memory challenge!"
                playAgainBtn.visibility = View.GONE
                exitBtn.text = "Main Menu"
                return true
            }
        }
        playAgainBtn.text = "NEXT LEVEL"
        return true
    }

    fun startMechanicTimers() {
        if (boss == Boss.Trickster) {
            startAutomaticShuffleTimer()
        }
    }

    fun onDestroy() {
        shuffleTimer?.cancel()
        bombTimer?.cancel()
    }

    // --- Trickster Methods ---
    private fun startAutomaticShuffleTimer() {
        shuffleTimer?.cancel()
        var isWarningShown = false
        shuffleTimer = object : CountDownTimer(SHUFFLE_INTERVAL, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (millisUntilFinished <= SHUFFLE_WARNING_TIME && !isWarningShown) {
                    showShuffleWarning()
                    isWarningShown = true
                }
            }
            override fun onFinish() {
                performShuffle()
                startAutomaticShuffleTimer()
            }
        }.start()
    }

    private fun performShuffle() {
        val adapter = activity.findViewById<GridView>(R.id.gridView).adapter as CardAdapter
        val currentlyRevealed = mutableSetOf<Int>()
        // Access properties directly
        activity.firstSelected?.let { currentlyRevealed.add(it) }
        activity.secondSelected?.let { currentlyRevealed.add(it) }

        val lockedPositions = activity.matchedPositions + currentlyRevealed
        val currentImages = activity.images
        val positionsToShuffle = (0 until currentImages.size).filter { it !in lockedPositions }
        val imagesToShuffle = positionsToShuffle.map { currentImages[it] }.shuffled()

        val newImagesArrangement = currentImages.toMutableList()
        positionsToShuffle.forEachIndexed { index, position ->
            newImagesArrangement[position] = imagesToShuffle[index]
        }

        // Access property directly
        activity.images = newImagesArrangement
        adapter.updateImages(newImagesArrangement)
        showShuffleComplete()
    }

    // --- Locksmith Methods ---
    fun getLockedPositions(): Set<Int> = lockedCardPositions
    fun isKeyFound(): Boolean = isKeyFound

    private fun selectLockedCards(images: List<Int>, numberOfCardsToLock: Int) {
        lockedCardPositions.clear()
        val nonKeyCardIndices = images.indices.filter { images[it] != keyImageResource }
        val positionsToLock = nonKeyCardIndices.shuffled().take(numberOfCardsToLock)
        lockedCardPositions.addAll(positionsToLock)
    }

    // --- Lynx (Bomb) Methods ---
    private fun startBombTimer() {
        showBombArmedMessage()
        isBombTicking = true
        // Access property directly
        activity.findViewById<TextView>(R.id.bombTimerTextView).visibility = View.VISIBLE

        bombTimer = object : CountDownTimer(15000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                activity.findViewById<TextView>(R.id.bombTimerTextView).text = "$secondsLeft"
            }
            override fun onFinish() = triggerBombPenalty()
        }.start()
    }

    private fun defuseBomb() {
        bombTimer?.cancel()
        isBombTicking = false
        activity.findViewById<TextView>(R.id.bombTimerTextView).visibility = View.GONE
        showBombDefusedMessage()
    }

    private fun triggerBombPenalty() {
        isBombTicking = false
        activity.findViewById<TextView>(R.id.bombTimerTextView).visibility = View.GONE
        val pairsToUnmatch = 2
        // Access properties directly
        val matchedPairsByImage = activity.matchedPositions.groupBy { activity.images[it] }
        val actualPairs = matchedPairsByImage.values.filter { it.size == 2 }
        val pairsToRemove = actualPairs.shuffled().take(pairsToUnmatch)

        val activityAsGame = activity
        for (pair in pairsToRemove) {
            activityAsGame.matchedPositions.remove(pair[0])
            activityAsGame.matchedPositions.remove(pair[1])
            (activityAsGame.findViewById<GridView>(R.id.gridView).adapter as CardAdapter).hideCard(pair[0])
            (activityAsGame.findViewById<GridView>(R.id.gridView).adapter as CardAdapter).hideCard(pair[1])
            activityAsGame.score--
            activityAsGame.matchedPairs--
        }
        activity.findViewById<TextView>(R.id.scoreTextView).text = "Score: ${activityAsGame.score}"

        showBombExplodedMessage {
            performShuffle()
        }
    }

    // --- Animation Helper Methods ---
    private fun showShuffleWarning() {
        val warningText = TextView(activity).apply {
            text = "Cards Shuffling in 3s!"
            textSize = 20f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.parseColor("#80000000"))
        }
        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(warningText, params)
        warningText.alpha = 0f
        warningText.animate()
            .alpha(1f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .start()

        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                warningText.text = "Cards Shuffling in ${secondsLeft}s!"
            }
            override fun onFinish() {
                warningText.animate()
                    .alpha(0f)
                    .scaleX(1.5f)
                    .scaleY(1.5f)
                    .setDuration(300)
                    .withEndAction { gameLayout.removeView(warningText) }
                    .start()
            }
        }.start()
    }

    private fun showShuffleComplete() {
        val shuffleText = TextView(activity).apply {
            text = "SHUFFLED! 😂"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#CCFF4444"))
        }
        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(shuffleText, frameParams)
        shuffleText.alpha = 0f
        shuffleText.scaleX = 0.5f
        shuffleText.scaleY = 0.5f
        shuffleText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .withEndAction {
                shuffleText.postDelayed({
                    shuffleText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { gameLayout.removeView(shuffleText) }
                        .start()
                }, 1200)
            }
            .start()
    }

    private fun showCardsUnlockedMessage() {
        val unlockedText = TextView(activity).apply {
            text = "All Cards Unlocked! 🔑"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B31E8A6D"))
        }

        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(unlockedText, frameParams)

        unlockedText.alpha = 0f
        unlockedText.scaleX = 0.5f
        unlockedText.scaleY = 0.5f
        unlockedText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .withEndAction {
                unlockedText.postDelayed({
                    unlockedText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { gameLayout.removeView(unlockedText) }
                        .start()
                }, 1500)
            }
            .start()
    }

    private fun showBombArmedMessage() {
        val armedText = TextView(activity).apply {
            text = "Timer Armed! Find the other bomb!"
            textSize = 22f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(armedText, frameParams)
        armedText.animate().alpha(0f).setStartDelay(2000).setDuration(500).withEndAction {
            gameLayout.removeView(armedText)
        }.start()
    }

    private fun showBombDefusedMessage() {
        val defusedText = TextView(activity).apply {
            text = "Bomb Defused! 💣"
            textSize = 22f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(defusedText, frameParams)
        defusedText.alpha = 0f
        defusedText.scaleX = 0.5f
        defusedText.scaleY = 0.5f
        defusedText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .withEndAction {
                defusedText.postDelayed({
                    defusedText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction { gameLayout.removeView(defusedText) }
                        .start()
                }, 1500)
            }
            .start()
    }

    private fun showBombExplodedMessage(onAnimationEnd: () -> Unit) {
        val explodedText = TextView(activity).apply {
            text = "BOOM! 💥"
            textSize = 30f
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        val gameLayout = activity.findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(explodedText, frameParams)
        explodedText.alpha = 0f
        explodedText.scaleX = 0.5f
        explodedText.scaleY = 0.5f
        explodedText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400)
            .withEndAction {
                explodedText.postDelayed({
                    explodedText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            gameLayout.removeView(explodedText)
                            onAnimationEnd()
                        }
                        .start()
                }, 1200)
            }
            .start()
    }
}