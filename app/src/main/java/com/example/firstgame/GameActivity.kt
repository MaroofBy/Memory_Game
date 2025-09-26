package com.example.firstgame

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit

class GameActivity : AppCompatActivity() {

    private lateinit var adapter: CardAdapter // <-- ADD THIS LINE

    private var isBombMechanicActive = false
    private val bombImageResource = R.drawable.ic_bomb // Make sure you have ic_bomb in your drawables
    private var bombTimer: CountDownTimer? = null
    private var isBombTicking = false
    private lateinit var bombTimerTextView: TextView

    private var isKeyMechanicActive = false
    private var isKeyFound = false
    private val lockedCardPositions = mutableSetOf<Int>()
    private val keyImageResource = R.drawable.ic_key

    private var isGameStarted = false
    private val matchedPositions = mutableSetOf<Int>()

    private var isShuffleModeActive = false
    private var shuffleTimer: CountDownTimer? = null

    private val SHUFFLE_INTERVAL = 15000L
    private val SHUFFLE_WARNING_TIME = 3000L


    private var score = 0
    private lateinit var gridView: GridView
    private lateinit var scoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var levelTextView: TextView
    private lateinit var playAgainButton: Button
    private var timer: CountDownTimer? = null
    private lateinit var flipSound: MediaPlayer
    private lateinit var winSound: MediaPlayer
    private lateinit var gameOverSound: MediaPlayer
    private lateinit var tickingSound: MediaPlayer
    private lateinit var winAnimation: Animation
    private lateinit var dialogFadeInAnimation: Animation

    private var timeLimitSeconds: Int = 0
    private var timeLeftInMillis: Long = 0
    private var currentPlayerId: String? = null
    private var currentPlayerName: String? = null
    private var currentDifficulty: String? = null
    private var currentLevel: Int = 0
    private var isSfxEnabled = true
    private var pauseDialog: AlertDialog? = null
    private var images = mutableListOf<Int>()
    private var firstSelected: Int? = null
    private var secondSelected: Int? = null
    private var matchedPairs = 0
    private var winDialog: AlertDialog? = null
    private var gameOverDialog: AlertDialog? = null
    private var hintsAvailable = 1

    // Add variables for dynamic sizing
    private var cardSize = 80 // Default size in dp
    private var numColumns = 4 // Default columns

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // First, find all your views
        gridView = findViewById(R.id.gridView)
        scoreTextView = findViewById(R.id.scoreTextView)
        timerTextView = findViewById(R.id.timerTextView)
        levelTextView = findViewById(R.id.levelTextView)
        playAgainButton = findViewById(R.id.playAgainButton)
        bombTimerTextView = findViewById(R.id.bombTimerTextView)

        // Now, get your game data
        currentPlayerName = intent.getStringExtra("player_name")
        currentPlayerId = intent.getStringExtra("player_id")
        currentDifficulty = intent.getStringExtra("difficulty")
        currentLevel = intent.getIntExtra("level_number", 0)

        // Now that levelTextView is initialized, you can use it
        if (currentLevel > 0) {
            levelTextView.text = "Level $currentLevel"
            levelTextView.visibility = View.VISIBLE
        } else {
            levelTextView.visibility = View.GONE
        }
        // In your onCreate method, after setting the level text:
        when (currentLevel) {
            10 -> {
                levelTextView.text = "LOCKSMITH"
                levelTextView.setTextColor(Color.YELLOW)
                levelTextView.textSize = 35f
                levelTextView.typeface = ResourcesCompat.getFont(this, R.font.carnival)            }
            20 -> {
                levelTextView.text = "TRICKSTER"
                levelTextView.setTextColor(Color.parseColor("#40E0D0"))
                levelTextView.textSize = 35f
                levelTextView.typeface = ResourcesCompat.getFont(this, R.font.carnival)            }
            30 -> {
                levelTextView.text = "LYNX"
                levelTextView.setTextColor(Color.parseColor("#DC143C"))
                levelTextView.textSize = 50f
                levelTextView.typeface = ResourcesCompat.getFont(this, R.font.carnival)            }
            36 -> {
                levelTextView.text = "SHADOW"
                levelTextView.setTextColor(Color.RED)
                levelTextView.textSize = 40f
                levelTextView.typeface = ResourcesCompat.getFont(this, R.font.carnival)
            }
            else -> {
                levelTextView.text = "Level $currentLevel"
                levelTextView.setTextColor(Color.WHITE)
            }
        }

        val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        isSfxEnabled = sharedPreferences.getBoolean("sfx_enabled", true)

        gridView.isSoundEffectsEnabled = false

        val pauseButton: ImageButton = findViewById(R.id.pauseButton)
        pauseButton.setColorFilter(Color.parseColor("#ADD8E6"), PorterDuff.Mode.SRC_IN)
        pauseButton.setOnClickListener {
            showPauseDialog()
        }

        flipSound = MediaPlayer.create(this, R.raw.card_flip)
        winSound = MediaPlayer.create(this, R.raw.win_sound)
        gameOverSound = MediaPlayer.create(this, R.raw.game_over_sound)
        tickingSound = MediaPlayer.create(this, R.raw.ticking_sound)
        winAnimation = AnimationUtils.loadAnimation(this, R.anim.win_animation)
        dialogFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)

        val uniqueImages = listOf(
            R.drawable.ic_star, R.drawable.ic_heart, R.drawable.ic_moon, R.drawable.ic_sun,
            R.drawable.ic_diamond, R.drawable.ic_club, R.drawable.ic_spade, R.drawable.ic_circle,
            R.drawable.ic_flower, R.drawable.ic_snowflake, R.drawable.ic_lightning, R.drawable.ic_acorn,
            R.drawable.ic_crown, R.drawable.ic_oxo, R.drawable.ic_gem, R.drawable.ic_shield,R.drawable.ic_treasure,
            R.drawable.ic_butterfly, R.drawable.ic_rocket, R.drawable.ic_fires, R.drawable.ic_axe,
            R.drawable.ic_skeleton, R.drawable.ic_sword, R.drawable.ic_ninja, R.drawable.ic_gun
        )

        var numPairs: Int = 0

        if (currentLevel > 0) {
            when (currentLevel) {
                // Levels 1-10: Beginners (4-20 cards)
                1 -> { numPairs = 2; timeLimitSeconds = 0; numColumns = 2 }   // 4 cards
                2 -> { numPairs = 3; timeLimitSeconds = 0; numColumns = 2 }   // 6 cards
                3 -> { numPairs = 4; timeLimitSeconds = 0; numColumns = 2 }   // 8 cards
                4 -> { numPairs = 5; timeLimitSeconds = 0; numColumns = 5 }   // 10 cards
                5 -> { numPairs = 6; timeLimitSeconds = 0; numColumns = 3 }   // 12 cards
                6 -> { numPairs = 7; timeLimitSeconds = 0; numColumns = 4 }   // 14 cards
                7 -> { numPairs = 8; timeLimitSeconds = 0; numColumns = 4 }   // 16 cards
                8 -> { numPairs = 9; timeLimitSeconds = 0; numColumns = 3 }   // 18 cards
                9 -> { numPairs = 9; timeLimitSeconds = 0; numColumns = 3 }  // 18 cards
                10 -> { numPairs = 10; timeLimitSeconds = 0; numColumns = 5;isKeyMechanicActive = true} // 20 cards

                // Levels 11-20: Intermediate (22-32 cards)
                11 -> { numPairs = 10; timeLimitSeconds = 100; numColumns = 5 } // 20 cards
                12 -> { numPairs = 11; timeLimitSeconds = 90; numColumns = 4 } // 22 cards
                13 -> { numPairs = 11; timeLimitSeconds = 80; numColumns = 4 } // 22 cards
                14 -> { numPairs = 12; timeLimitSeconds = 90; numColumns = 4 } // 24 cards
                15 -> { numPairs = 12; timeLimitSeconds = 80; numColumns = 4 } // 24 cards
                16 -> { numPairs = 13; timeLimitSeconds = 100; numColumns = 4 } // 26 cards
                17 -> { numPairs = 13; timeLimitSeconds = 80; numColumns = 4 } // 26 cards
                18 -> { numPairs = 14; timeLimitSeconds = 100; numColumns = 4 } // 28 cards
                19 -> { numPairs = 14; timeLimitSeconds = 80; numColumns = 4 } // 28 cards
                20 -> { numPairs = 12; timeLimitSeconds = 100; numColumns = 4; isShuffleModeActive = true  } // 30 cards


                // Levels 21-30: Advanced (40-50 cards)
                21 -> { numPairs = 15; timeLimitSeconds = 90; numColumns = 5 } // 30 cards
                22-> { numPairs = 16; timeLimitSeconds = 100; numColumns = 4 } // 32 cards
                23 -> { numPairs = 16; timeLimitSeconds = 120; numColumns = 6 } // 32 cards (different layout)
                24 -> { numPairs = 17; timeLimitSeconds = 120; numColumns = 6 } // 34 cards
                25 -> { numPairs = 17; timeLimitSeconds = 100; numColumns = 6 } // 34 cards
                26 -> { numPairs = 18; timeLimitSeconds = 140; numColumns = 6 } // 36 cards
                27 -> { numPairs = 18; timeLimitSeconds = 120; numColumns = 6 } // 36 cards
                28 -> { numPairs = 19; timeLimitSeconds = 140; numColumns = 6 } // 38 cards
                29 -> { numPairs = 19; timeLimitSeconds = 130; numColumns = 6 } // 38 cards
                30 -> { numPairs = 20; timeLimitSeconds = 160; numColumns = 5; isBombMechanicActive = true } // 40 cards


                // Levels 31-36: Expert (44-50 cards) - Final challenge
                31 -> { numPairs = 20; timeLimitSeconds = 150; numColumns = 5 } // 40 cards
                32 -> { numPairs = 21; timeLimitSeconds = 160; numColumns = 6 } // 42 cards
                33 -> { numPairs = 22; timeLimitSeconds = 160; numColumns = 6 } // 44 cards
                34 -> { numPairs = 23; timeLimitSeconds = 160; numColumns = 6 } // 46 cards
                35 -> { numPairs = 24; timeLimitSeconds = 150; numColumns = 8 } // 48 cards (6×8)
                36 -> { numPairs = 25; timeLimitSeconds = 130; numColumns = 8 } // 50 cards (8×7)


                else -> {
                    numPairs = 24
                    timeLimitSeconds = 90
                    numColumns = 6
                }
            }


        } else {
            when (currentDifficulty) {
                "easy" -> { numPairs = 6; numColumns = 3; timeLimitSeconds = 60 }
                "normal" -> { numPairs = 10; numColumns = 4; timeLimitSeconds = 60 }
                "hard" -> { numPairs = 15; numColumns = 5; timeLimitSeconds = 70 }
                "insane" -> { numPairs = 18; numColumns = 6; timeLimitSeconds = 90 }
                "impossible" -> { numPairs = 24; numColumns = 6; timeLimitSeconds = 125 }
                else -> { numPairs = 3; numColumns = 3; timeLimitSeconds = 75 }
            }
        }

        // Calculate dynamic card size based on screen width and number of columns
        val numRows = (numPairs * 2 + numColumns - 1) / numColumns
        cardSize = calculateCardSize(numColumns, numRows)
        gridView.numColumns = numColumns

        // --- Conditionally start the timer ---


        // ADD THIS NEW LOGIC
        if (isKeyMechanicActive) {
            // Logic for Level 20: Ensure the key is present
            val otherImages = uniqueImages.filter { it != keyImageResource }.shuffled().take(numPairs - 1)
            val finalUniqueImages = otherImages + keyImageResource
            val imagePairs = finalUniqueImages + finalUniqueImages
            images.clear()
            images.addAll(imagePairs.shuffled().toMutableList())

            // Now, select which non-key cards to lock
            selectLockedCards(6) // We will lock 6 cards (3 pairs)
        }  else if (isBombMechanicActive) { // <-- ADD THIS ENTIRE BLOCK
            val otherImages =
                uniqueImages.filter { it != bombImageResource }.shuffled().take(numPairs - 1)
            val finalUniqueImages = otherImages + bombImageResource
            val imagePairs = finalUniqueImages + finalUniqueImages
            images.clear()
            images.addAll(imagePairs.shuffled().toMutableList())
        }
        else {
            // This is your original logic for all other levels
            val selectedUniqueImages = uniqueImages.shuffled().take(numPairs)
            val imagePairs = selectedUniqueImages + selectedUniqueImages
            images.clear()
            images.addAll(imagePairs.shuffled().toMutableList())
        }

        adapter = CardAdapter(this, images, cardSize, lockedCardPositions, isKeyFound)

        if (currentLevel == 36) {
            adapter.enableFogOfWar()
        }
        gridView.adapter = adapter
        gridView.setOnItemClickListener { _, view, position, _ ->
            if (isKeyMechanicActive && !isKeyFound && position in lockedCardPositions) {
                Toast.makeText(this, "Find the key pair to unlock these cards!", Toast.LENGTH_SHORT).show()
                return@setOnItemClickListener // Ignore the click
            }

            if (adapter.isCardRevealed(position) ||
                (firstSelected != null && secondSelected != null)) {
                return@setOnItemClickListener
            }
            if (currentLevel == 36 && !adapter.isPositionRevealed(position)) {
                return@setOnItemClickListener // Can't click on fogged cards
            }
            val flipAnimation = AlphaAnimation(0f, 1f).apply { duration = 200 }
            view.startAnimation(flipAnimation)
            if (isSfxEnabled) flipSound.start()

            onCardFlipped(position)

        }

        playAgainButton.setOnClickListener {
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("player_name", currentPlayerName)
            intent.putExtra("player_id", currentPlayerId)
            if (currentLevel > 0) {
                intent.putExtra("level_number", currentLevel)
            } else {
                intent.putExtra("difficulty", currentDifficulty)
            }
            startActivity(intent)
            finish()
        }

        if (timeLimitSeconds > 0) {
            timerTextView.text = "Time Left: ${timeLimitSeconds}s"
        } else {
            timerTextView.text = "No Time Limit"
        }
        when (currentLevel) {
            10 ->{
                val title = "LOCKSMITH: Locked Cards! 🔒"
                val message = "Some cards are locked! You must find the key pair first to unlock the rest of the board."
                showTutorialIfNeeded(currentLevel, "seen_level_20_lock_tutorial", title, message)
            }
            20 -> {
                val title = "TRICKSTER: Watch Out!"
                val message = "The cards on this level will automatically shuffle every 15 seconds! Only hidden and unmatched cards will move."
                showTutorialIfNeeded(currentLevel, "seen_level_${currentLevel}_tutorial", title, message)
            }
            30 -> {
                val title = "LYNX: Defuse the Bomb! 💣"
                val message = "This level has a hidden bomb pair! If you flip the first one, a 15-second timer starts. Find the second bomb to defuse it, or you'll face a heavy penalty!"
                showTutorialIfNeeded(currentLevel, "seen_level_30_bomb_tutorial", title, message)
            }
            36 -> {
                val title = "SHADOW: Fog of War"
                val message = "The board is hidden in fog! For every 3 pairs you match, a new set of cards will be revealed."
                showTutorialIfNeeded(currentLevel, "seen_level_36_tutorial", title, message)
            }
            else -> {
                startGameTimers()
            }
        }
    }
    private fun onCardFlipped(position: Int) {
        // 1. Reveal the card visually
        adapter.revealCard(position)

        // 2. IMMEDIATELY check if the revealed card is an unarmed bomb
        if (isBombMechanicActive && !isBombTicking && images[position] == bombImageResource) {
            startBombTimer()
        }

        // 3. Now, handle the matching logic
        if (firstSelected == null) {
            // This is the first card of a turn
            firstSelected = position
        } else {
            // This is the second card of a turn
            secondSelected = position
            checkMatch(adapter)
        }
    }
    private fun showBombArmedMessage() {
        val armedText = TextView(this).apply {
            text = "Timer Armed! Find the other bomb!"
            textSize = 22f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }
        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
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
        val defusedText = TextView(this).apply {
            text = "Bomb Defused! 💣"
            textSize = 22f
            setTextColor(Color.GREEN)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))
        }

        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(defusedText, frameParams)

        // --- Animation Logic ---

        // 1. Pop the text in
        defusedText.alpha = 0f
        defusedText.scaleX = 0.5f
        defusedText.scaleY = 0.5f
        defusedText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400) // Quick pop-in animation
            .withEndAction {
                // 2. After a delay, fade the text out
                defusedText.postDelayed({
                    defusedText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            gameLayout.removeView(defusedText)
                        }
                        .start()
                }, 1500) // Keep message on screen for 1.5 seconds
            }
            .start()
    }

    private fun showBombExplodedMessage(onAnimationEnd: () -> Unit) { // <-- 1. ADD THIS PARAMETER
        val explodedText = TextView(this).apply {
            text = "BOOM! 💥"
            textSize = 30f
            setTextColor(Color.RED)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B3000000"))

        }

        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(explodedText, frameParams)

        // Pop-in and fade-out animation
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
                            onAnimationEnd() // <-- 2. CALL THE ACTION HERE
                        }
                        .start()
                }, 1200)
            }
            .start()
    }
    private fun startBombTimer() {
        showBombArmedMessage()
        isBombTicking = true
        bombTimerTextView.visibility = View.VISIBLE

        bombTimer = object : CountDownTimer(15000, 1000) { // 15-second timer
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = (millisUntilFinished / 1000) + 1
                bombTimerTextView.text = "$secondsLeft"
                // Optional: Add a pulsing animation or ticking sound here
            }

            override fun onFinish() {
                // Time ran out! Trigger the penalty.
                triggerBombPenalty()
            }
        }.start()
    }

    private fun defuseBomb() {

        bombTimer?.cancel()
        isBombTicking = false
        bombTimerTextView.visibility = View.GONE
        showBombDefusedMessage()        // Optional: Play a success sound
    }

    private fun triggerBombPenalty() {
        isBombTicking = false
        bombTimerTextView.visibility = View.GONE

        val pairsToUnmatch = 2

        // --- NEW, CORRECTED LOGIC TO UN-MATCH PAIRS ---

        // 1. Find all currently matched pairs by grouping positions by their image
        val matchedPairsByImage = matchedPositions.groupBy { images[it] }
        val actualPairs = matchedPairsByImage.values.filter { it.size == 2 } // Ensure we only have pairs

        // 2. Shuffle them to make the penalty random and take the number we need
        val pairsToRemove = actualPairs.shuffled().take(pairsToUnmatch)

        // 3. Loop through the chosen pairs and un-match them
        for (pair in pairsToRemove) {
            val pos1 = pair[0]
            val pos2 = pair[1]

            matchedPositions.remove(pos1)
            matchedPositions.remove(pos2)
            (gridView.adapter as CardAdapter).hideCard(pos1)
            (gridView.adapter as CardAdapter).hideCard(pos2)

            score--
            this.matchedPairs-- // Use this.matchedPairs to be specific
        }

        // Update the score on screen once after all changes
        scoreTextView.text = "Score: $score"

        // 4. Show the "BOOM!" message and then shuffle the board
        showBombExplodedMessage {
            performShuffle()
        }
    }
    private fun selectLockedCards(numberOfCardsToLock: Int) {
        lockedCardPositions.clear()
        val nonKeyCardIndices = images.indices.filter { images[it] != keyImageResource }

        // Take a few random non-key cards to lock
        val positionsToLock = nonKeyCardIndices.shuffled().take(numberOfCardsToLock)
        lockedCardPositions.addAll(positionsToLock)
    }
    private fun showCardsUnlockedMessage() {
        val unlockedText = TextView(this).apply {
            text = "All Cards Unlocked! 🔑"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#B31E8A6D")) // A nice green color
        }

        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(unlockedText, frameParams)

        // Pop-in and fade-out animation
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
                }, 1500) // Keep on screen for 1.5 seconds
            }
            .start()
    }
    private fun startGameTimers() {
        isGameStarted = true // <-- ADD THIS LINE

        if (timeLimitSeconds > 0) {
            timeLeftInMillis = (timeLimitSeconds * 1000).toLong()
            startTimer()
        }

        // Start the shuffle timer if it's a shuffle level
        if (isShuffleModeActive) {
            startAutomaticShuffleTimer()
        }
    }
    private fun showTutorialIfNeeded(level: Int, prefKey: String, title: String, message: String) {
        val sharedPreferences = getSharedPreferences("game_tutorials", Context.MODE_PRIVATE)
        val hasSeenTutorial = sharedPreferences.getBoolean(prefKey, false)

        // If the player has NOT seen the tutorial yet...
        if (!hasSeenTutorial) {
            // Create and show the tutorial dialog
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Got It!") { dialog, _ ->
                    // When the player clicks "Got It!", save the preference...
                    sharedPreferences.edit().putBoolean(prefKey, true).apply()
                    // ...and then start the game timers.
                    startGameTimers()
                    dialog.dismiss()
                }
                .setCancelable(false) // Prevents closing by tapping outside
                .show()
        } else {
            // If they have already seen it, just start the timers immediately.
            startGameTimers()
        }
    }
    private fun startAutomaticShuffleTimer() {
        shuffleTimer?.cancel() // Cancel any existing timer
        var isWarningShown = false // Prevents the warning from showing multiple times

        shuffleTimer = object : CountDownTimer(SHUFFLE_INTERVAL, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Show a warning 2 seconds before the shuffle
                if (millisUntilFinished <= SHUFFLE_WARNING_TIME && !isWarningShown) {
                    showShuffleWarning()
                    isWarningShown = true
                }
            }

            override fun onFinish() {
                performShuffle()
                // Restart the timer to create a continuous loop
                startAutomaticShuffleTimer()
            }
        }.start()
    }

    private fun showShuffleWarning() {
        // Create the TextView
        val warningText = TextView(this).apply {
            text = "Cards Shuffling in 3s!" // Initial text
            textSize = 20f
            setTextColor(Color.YELLOW)
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            setBackgroundColor(Color.parseColor("#80000000"))
        }

        // Add it to the main layout
        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(warningText, params)

        // --- NEW Animation & Countdown Logic ---

        // 1. Animate the text fading in
        warningText.alpha = 0f
        warningText.animate()
            .alpha(1f)
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(300)
            .start()

        // 2. Start a 3-second timer to update the text
        object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update the text every second
                val secondsLeft = (millisUntilFinished / 1000) + 1
                warningText.text = "Cards Shuffling in ${secondsLeft}s!"
            }

            override fun onFinish() {
                // 3. When the timer finishes, "pop" the text out and remove it
                warningText.animate()
                    .alpha(0f)
                    .scaleX(1.5f)
                    .scaleY(1.5f)
                    .setDuration(300)
                    .withEndAction {
                        gameLayout.removeView(warningText)
                    }
                    .start()
            }
        }.start()
    }

    private fun performShuffle() {
        val adapter = gridView.adapter as CardAdapter

        // 1. Define all "locked" cards that shouldn't move
        val currentlyRevealed = mutableSetOf<Int>()
        if (firstSelected != null) currentlyRevealed.add(firstSelected!!)
        if (secondSelected != null) currentlyRevealed.add(secondSelected!!)

        val lockedPositions = matchedPositions + currentlyRevealed

        // 2. Get the positions and images of cards that are free to move
        val positionsToShuffle = (0 until images.size).filter { it !in lockedPositions }
        val imagesToShuffle = positionsToShuffle.map { images[it] }.shuffled()

        // 3. Create a new arrangement for the grid
        val newImagesArrangement = images.toMutableList()

        // 4. Place the shuffled images back into the available positions
        positionsToShuffle.forEachIndexed { index, position ->
            newImagesArrangement[position] = imagesToShuffle[index]
        }

        // 5. Update the main images list and notify the adapter
        images = newImagesArrangement
        adapter.updateImages(images) // Use our new adapter function

        showShuffleComplete() // Optional: feedback to the user
    }

    private fun showShuffleComplete() {
        val shuffleText = TextView(this).apply {
            text = "SHUFFLED! 😂"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(30, 15, 30, 15)
            setBackgroundColor(Color.parseColor("#CCFF4444")) // Red background
        }

        val gameLayout = findViewById<ViewGroup>(android.R.id.content)
        val frameParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        gameLayout.addView(shuffleText, frameParams)

        // --- NEW Professional Animation ---

        // 1. Pop the text in
        shuffleText.alpha = 0f
        shuffleText.scaleX = 0.5f
        shuffleText.scaleY = 0.5f
        shuffleText.animate()
            .alpha(1f)
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(400) // A quick pop-in
            .withEndAction {
                // 2. After a delay, fade the text out
                shuffleText.postDelayed({
                    shuffleText.animate()
                        .alpha(0f)
                        .setDuration(300)
                        .withEndAction {
                            gameLayout.removeView(shuffleText)
                        }
                        .start()
                }, 1200) // Keep it on screen for 1.2 seconds
            }
            .start()

    }


    private fun calculateCardSize(columns: Int, rows: Int): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenDensity = displayMetrics.density

        val screenWidthDp = (screenWidth / screenDensity).toInt()
        val screenHeightDp = (screenHeight / screenDensity).toInt()

        // Only apply strict sizing if more than 5 rows OR more than 6 columns
        if (rows > 5 || columns > 6) {
            // Strict sizing for large grids
            val availableHeight = screenHeightDp - 280
            val availableWidth = screenWidthDp - 50
            val cardSizeFromWidth = (availableWidth / columns) - 12
            val cardSizeFromHeight = (availableHeight / rows) - 12
            val calculatedSize = minOf(cardSizeFromWidth, cardSizeFromHeight)

            return when {
                calculatedSize < 35 -> 35
                calculatedSize > 55 -> 55
                else -> calculatedSize
            }
        } else {
            // Normal sizing for grids 5×6 or smaller
            val availableWidth = screenWidthDp - 40
            val cardSizeFromWidth = (availableWidth / columns) - 8

            return when {
                cardSizeFromWidth < 50 -> 50
                cardSizeFromWidth > 80 -> 80
                else -> cardSizeFromWidth
            }
        }
    }

    private fun updateMusicToggleButtonIcon(button: ImageButton) {
        if (MusicManager.isMusicPlaying) {
            button.setImageResource(R.drawable.ic_music_on)
        } else {
            button.setImageResource(R.drawable.ic_music_off)
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = object : CountDownTimer(timeLeftInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeftInMillis = millisUntilFinished
                val secondsLeft = millisUntilFinished / 1000
                timerTextView.text = "Time Left: ${secondsLeft}s"

                if (secondsLeft <= 5) {
                    timerTextView.setTextColor(Color.RED)
                    if (isSfxEnabled && ::tickingSound.isInitialized && !tickingSound.isPlaying) {
                        tickingSound.start()
                    }
                } else {
                    timerTextView.setTextColor(Color.WHITE)
                    if (::tickingSound.isInitialized && tickingSound.isPlaying) {
                        tickingSound.pause()
                        tickingSound.seekTo(0)
                    }
                }
            }

            override fun onFinish() {
                timerTextView.setTextColor(Color.WHITE)
                if (::tickingSound.isInitialized && tickingSound.isPlaying) {
                    tickingSound.pause()
                    tickingSound.seekTo(0)
                }
                showGameOverDialog()
            }
        }.start()
    }

    private fun checkMatch(adapter: CardAdapter) {
        if (firstSelected != null && secondSelected != null) {
            if (images[firstSelected!!] == images[secondSelected!!]) {
                if (isBombMechanicActive && isBombTicking && images[firstSelected!!] == bombImageResource) {
                    defuseBomb()
                }
                if (isKeyMechanicActive && !isKeyFound && images[firstSelected!!] == keyImageResource) {
                    isKeyFound = true
                    showCardsUnlockedMessage()

                    (gridView.adapter as CardAdapter).unlockAllCards()
                    // Optional: Show a "Keys Found!" message here
                }
                matchedPositions.add(firstSelected!!)
                matchedPositions.add(secondSelected!!)
                matchedPairs++
                score++

                if (currentLevel == 36) {
                    adapter.onMatchMade() // Add this line back
                }
                scoreTextView.text = "Score: $score"
                if (matchedPairs == images.size / 2) {
                    timer?.cancel()
                    if (::tickingSound.isInitialized && tickingSound.isPlaying) {
                        tickingSound.pause()
                        tickingSound.seekTo(0)
                    }
                    showWinDialog()
                }
                firstSelected = null
                secondSelected = null
            } else {
                gridView.postDelayed({
                    adapter.hideCard(firstSelected!!)
                    adapter.hideCard(secondSelected!!)
                    firstSelected = null
                    secondSelected = null
                }, 500)
            }
        }
    }

    private fun showWinDialog() {
        timer?.cancel()
        if (isSfxEnabled) winSound.start()

        currentPlayerId?.let { playerId ->
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(playerId)

            userRef.get()
                .addOnSuccessListener { document ->
                    val currentHighScore = document.getLong("score") ?: 0
                    val currentHighestLevel = document.getLong("highest_level")?.toInt() ?: 0
                    val currentBestTime = document.getLong("best_time") ?: Long.MAX_VALUE
                    val currentChallengeMode = document.getString("challengeMode") ?: "none"

                    val difficultyOrder = listOf("easy", "normal", "hard", "insane", "impossible")
                    val newDifficultyRank = difficultyOrder.indexOf(currentDifficulty)
                    val oldDifficultyRank = difficultyOrder.indexOf(currentChallengeMode)
                    val newBestTime = (timeLimitSeconds * 1000 - timeLeftInMillis)

                    val updateData = mutableMapOf<String, Any>()

                    if (score > currentHighScore) {
                        updateData["score"] = score.toLong()
                    }

                    // --- MODIFIED LOGIC ---
                    // Only update highest_level if it's not the final level
                    val totalLevels = 36
                    if (currentLevel > 0 && currentLevel > currentHighestLevel && currentLevel < totalLevels) {
                        updateData["highest_level"] = currentLevel + 1 // Firestore tracks the next unlocked level
                    }
                    // --- END MODIFICATION ---

                    if (currentDifficulty != null && currentDifficulty != "none") {
                        if (newDifficultyRank > oldDifficultyRank ||
                            (newDifficultyRank == oldDifficultyRank && newBestTime < currentBestTime)) {
                            updateData["challengeMode"] = currentDifficulty!!
                            updateData["best_time"] = newBestTime
                        }
                    }

                    if (updateData.isNotEmpty()) {
                        userRef.update(updateData)
                            .addOnSuccessListener { Log.d("Firestore", "User data updated successfully") }
                            .addOnFailureListener { e -> Log.e("Firestore", "Error updating user data", e) }
                    } else {
                        Log.d("Firestore", "No new high score or better challenge mode result.")
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error getting user document to check high score", e)
                }

            // --- MODIFIED LOGIC ---
            // Only unlock the next level in SharedPreferences if it's not the final level
            val totalLevels = 36
            if (currentLevel > 0 && currentLevel < totalLevels) {
                val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
                val highestUnlockedLevel = sharedPreferences.getInt("highest_unlocked_level", 1)

                if (currentLevel >= highestUnlockedLevel) {
                    sharedPreferences.edit().putInt("highest_unlocked_level", currentLevel + 1).apply()
                }
            }
            // --- END MODIFICATION ---
        }

        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_win, null)
        builder.setView(customLayout)
        builder.setCancelable(false)
        winDialog = builder.create()

        val konfettiView = customLayout.findViewById<KonfettiView>(R.id.konfettiView)


        val leftBottomParty = Party(
            speed = 0f, // Much slower initial speed
            maxSpeed = 7f, // Reduced max speed
            damping = 0.99f, // Higher damping for slower fall
            spread = 50,
            angle = 315, // Up and to the right (315 degrees = -45 degrees)
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.35, 0.5), // Bottom-left of trophy
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100) // longer + more pieces
        )
        // Right bottom confetti (from bottom-right of trophy)
        val rightBottomParty = Party(
            speed = 0f, // Much slower initial speed
            maxSpeed = 7f, // Reduced max speed
            damping = 0.99f, // Higher damping for slower fall
            spread = 50,
            angle = 225, // Up and to the left (225 degrees = -135 degrees)
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.65, 0.5), // Bottom-right of trophy
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100) // longer + more pieces
        )
        val topParty = Party(
            speed = 0f, // Very slow initial speed
            maxSpeed = 7f, // Reduced max speed
            damping = 0.99f, // Higher damping
            spread = 70,
            angle = 270, // Straight up
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.5, 0.3), // Top of trophy
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100) // longer + more pieces
        )

        // Start confetti effects
        konfettiView.start(leftBottomParty, rightBottomParty, topParty)



        val titleText = customLayout.findViewById<TextView>(R.id.dialog_title)
        val messageText = customLayout.findViewById<TextView>(R.id.dialog_message)
        val playAgainButton = customLayout.findViewById<Button>(R.id.play_again_button)
        val exitButton = customLayout.findViewById<Button>(R.id.exit_button)
        val trophyImage = customLayout.findViewById<ImageView>(R.id.trophy_image)

        customLayout.findViewById<ImageView>(R.id.trophy_image).startAnimation(winAnimation)

        // --- START OF ADDED LOGIC ---
        if (currentLevel == 10) {
            titleText.text = "LOCKSMITH DEFEATED!"
            messageText.text = "A master of keys! Your sharp mind has unlocked the path forward."
            playAgainButton.text = "NEXT LEVEL"
            trophyImage.setImageResource(R.drawable.ic_award)
            trophyImage.startAnimation(winAnimation)

            playAgainButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId) // Fixed
                intent.putExtra("level_number", currentLevel + 1)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                intent.putExtra("show_level_selection", true)
                startActivity(intent)
                finish()
            }

        } else if (currentLevel == 20) {
            titleText.text = "TRICKSTER DEFEATED!"
            messageText.text = "Congratulations! You outsmarted the chaotic shuffle."
            playAgainButton.text = "NEXT LEVEL"
            trophyImage.startAnimation(winAnimation)

            trophyImage.setImageResource(R.drawable.ic_award)
            playAgainButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId) // Fixed
                intent.putExtra("level_number", currentLevel + 1)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                intent.putExtra("show_level_selection", true)
                startActivity(intent)
                finish()
            }

        } else if (currentLevel == 30) {
            titleText.text = "LYNX DEFEATED!"
            messageText.text = "Congratulations! You kept your cool under pressure."
            playAgainButton.text = "NEXT LEVEL"
            trophyImage.startAnimation(winAnimation)

            trophyImage.setImageResource(R.drawable.ic_award)
            playAgainButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId) // Fixed
                intent.putExtra("level_number", currentLevel + 1)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                intent.putExtra("show_level_selection", true)
                startActivity(intent)
                finish()
            }

        } else if (currentLevel == 36) {
            titleText.text = "SHADOW DEFEATED!"
            messageText.text = "Congratulations! You've conquered the ultimate memory challenge!"
            trophyImage.setImageResource(R.drawable.ic_award)
            trophyImage.startAnimation(winAnimation)

            playAgainButton.visibility = View.GONE
            exitButton.text = "Main Menu"
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                startActivity(intent)
                finish()
            }

        } else if (currentLevel > 0) {
            messageText.text = "You won with a score of $score!"
            playAgainButton.text = "NEXT LEVEL"
            playAgainButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId) // Fixed
                intent.putExtra("level_number", currentLevel + 1)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                intent.putExtra("show_level_selection", true)
                startActivity(intent)
                finish()
            }

        } else {
            messageText.text = "You won with a score of $score!"
            playAgainButton.setOnClickListener {
                winDialog?.dismiss()
                val intent = Intent(this@GameActivity, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId) // Fixed
                intent.putExtra("difficulty", currentDifficulty)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                winDialog?.dismiss()
                finish()
            }
        }
        // --- END OF ADDED LOGIC ---
        trophyImage.startAnimation(winAnimation)

        winDialog?.show()
        customLayout.startAnimation(dialogFadeInAnimation)
    }

    private fun showGameOverDialog() {
        timer?.cancel()

        if (isSfxEnabled) gameOverSound.start()

        currentPlayerId?.let { playerId ->
            val db = FirebaseFirestore.getInstance()
            val userRef = db.collection("users").document(playerId)
            userRef.get()
                .addOnSuccessListener { document ->
                    val currentHighScore = document.getLong("score") ?: 0
                    if (score > currentHighScore) {
                        val data = hashMapOf<String, Any>(
                            "score" to score.toLong(),
                            "Name" to (currentPlayerName ?: "Anonymous")
                        )
                        userRef.set(data, SetOptions.merge())
                            .addOnSuccessListener { Log.d("Firestore", "New high score of $score set for $currentPlayerName after game over.") }
                    }
                }
        }

        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_game_over, null)
        val gameOverGifView = customLayout.findViewById<GifView>(R.id.game_over_gif)

        gameOverGifView.setGifResource(R.drawable.sad_gif)

        builder.setView(customLayout)
        builder.setCancelable(false)
        gameOverDialog = builder.create()

        val messageText = customLayout.findViewById<TextView>(R.id.dialog_message)
        val playAgainButton = customLayout.findViewById<Button>(R.id.play_again_button)
        val exitButton = customLayout.findViewById<Button>(R.id.exit_button)

        messageText.text = "Your score is $score. Try again?"

        if (currentLevel > 0) {
            playAgainButton.text = "RETRY"
            playAgainButton.setOnClickListener {
                gameOverDialog?.dismiss()
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId)
                intent.putExtra("level_number", currentLevel)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                gameOverDialog?.dismiss()
                val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                intent.putExtra("show_level_selection", true)
                startActivity(intent)
                finish()
            }
        } else {
            playAgainButton.setOnClickListener {
                gameOverDialog?.dismiss()
                val intent = Intent(this, GameActivity::class.java)
                intent.putExtra("player_name", currentPlayerName)
                intent.putExtra("player_id", currentPlayerId)
                intent.putExtra("difficulty", currentDifficulty)
                startActivity(intent)
                finish()
            }
            exitButton.setOnClickListener {
                gameOverDialog?.dismiss()
                finish()
            }
        }

        gameOverDialog?.show()
        customLayout.startAnimation(dialogFadeInAnimation)
    }

    private fun showPauseDialog() {
        timer?.cancel()
        if (isSfxEnabled && ::tickingSound.isInitialized && tickingSound.isPlaying) {
            tickingSound.pause()
        }

        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_pause, null)
        builder.setView(customLayout)
        builder.setCancelable(false)
        pauseDialog = builder.create()

        val musicToggleButton = customLayout.findViewById<ImageButton>(R.id.musicToggleButton)
        val sfxToggleButton = customLayout.findViewById<ImageButton>(R.id.sfxToggleButton)
        val homeButton = customLayout.findViewById<ImageButton>(R.id.homeButton)
        val resumeButton = customLayout.findViewById<ImageButton>(R.id.resumeButton)
        val backButton = customLayout.findViewById<ImageButton>(R.id.backButton)

        updateMusicToggleButtonIcon(musicToggleButton)
        sfxToggleButton.setImageResource(if (isSfxEnabled) R.drawable.ic_sfx_on else R.drawable.ic_sfx_off)

        if (currentLevel > 0) {
            backButton.visibility = View.VISIBLE
        } else {
            if (currentDifficulty == "time_challenge") {
                backButton.visibility = View.GONE
            } else {
                backButton.visibility = View.VISIBLE
            }
        }

        musicToggleButton.setOnClickListener {
            MusicManager.toggleMusic()
            updateMusicToggleButtonIcon(musicToggleButton)
        }

        // In your GameActivity pause dialog
        sfxToggleButton.setOnClickListener {
            isSfxEnabled = !isSfxEnabled

            // IMMEDIATELY save to SharedPreferences
            val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("sfx_enabled", isSfxEnabled).apply()

            sfxToggleButton.setImageResource(if (isSfxEnabled) R.drawable.ic_sfx_on else R.drawable.ic_sfx_off)
        }

        homeButton.setOnClickListener {
            showConfirmationDialog(
                message = "Are you sure you want to go to the main menu?",
                onConfirm = {
                    pauseDialog?.dismiss()
                    val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            )
        }

        resumeButton.setOnClickListener {
            pauseDialog?.dismiss()
            if (timeLimitSeconds > 0) {
                startTimer()
            }
        }

        backButton.setOnClickListener {
            showConfirmationDialog(
                message = "Are you sure you want to quit this game?",
                onConfirm = {
                    pauseDialog?.dismiss()
                    val intent = Intent(this@GameActivity, MainSplashActivity::class.java)
                    if (currentLevel > 0) {
                        intent.putExtra("show_level_selection", true)
                    } else {
                        intent.putExtra("show_difficulty_selection", true)
                    }
                    startActivity(intent)
                    finish()
                }
            )
        }

        pauseDialog?.show()
    }

    private fun showConfirmationDialog(message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setMessage(message)
            .setPositiveButton("Yes") { dialog, _ ->
                onConfirm()
                dialog.dismiss()
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        MusicManager.resumeMusic()
        val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        isSfxEnabled = sharedPreferences.getBoolean("sfx_enabled", true)
        if (timeLimitSeconds > 0 && isGameStarted) {
            startTimer()
        }
    }

    override fun onPause() {
        super.onPause()
        MusicManager.pauseMusic()
        timer?.cancel()
        if (isSfxEnabled && ::tickingSound.isInitialized && tickingSound.isPlaying) {
            tickingSound.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shuffleTimer?.cancel()
        flipSound.release()
        winSound.release()
        gameOverSound.release()
        tickingSound.release()
        timer?.cancel()
        winDialog?.dismiss()
        gameOverDialog?.dismiss()
        pauseDialog?.dismiss()
    }

    override fun onBackPressed() {
        showConfirmationDialog(
            message = "Are you sure you want to quit the game?",
            onConfirm = {
                super.onBackPressed()
            }
        )
    }
}