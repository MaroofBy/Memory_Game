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
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit
import android.view.animation.AlphaAnimation

class GameActivity : AppCompatActivity() {

    private lateinit var adapter: CardAdapter
    private lateinit var bossManager: BossManager

    // --- Game State Variables ---
    private var isGameStarted = false
    val matchedPositions = mutableSetOf<Int>()
    var score = 0
    var matchedPairs = 0
    var firstSelected: Int? = null
    var secondSelected: Int? = null

    // --- UI Views ---
    private lateinit var gridView: GridView
    private lateinit var scoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var levelTextView: TextView
    private lateinit var playAgainButton: Button
    private lateinit var bombTimerTextView: TextView

    // --- Timers & Sounds ---
    private var timer: CountDownTimer? = null
    private lateinit var flipSound: MediaPlayer
    private lateinit var winSound: MediaPlayer
    private lateinit var gameOverSound: MediaPlayer
    private lateinit var tickingSound: MediaPlayer

    // --- Animations & Dialogs ---
    private lateinit var winAnimation: Animation
    private lateinit var dialogFadeInAnimation: Animation
    private var winDialog: AlertDialog? = null
    private var gameOverDialog: AlertDialog? = null
    private var pauseDialog: AlertDialog? = null

    // --- Game Config Variables ---
    private var timeLimitSeconds: Int = 0
    private var timeLeftInMillis: Long = 0
    private var currentPlayerId: String? = null
    private var currentPlayerName: String? = null
    private var currentDifficulty: String? = null
    private var currentLevel: Int = 0
    private var isSfxEnabled = true
    var images = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        // --- Find Views ---
        gridView = findViewById(R.id.gridView)
        scoreTextView = findViewById(R.id.scoreTextView)
        timerTextView = findViewById(R.id.timerTextView)
        levelTextView = findViewById(R.id.levelTextView)
        playAgainButton = findViewById(R.id.playAgainButton)
        bombTimerTextView = findViewById(R.id.bombTimerTextView)

        // --- Load Game Data ---
        currentPlayerName = intent.getStringExtra("player_name")
        currentPlayerId = intent.getStringExtra("player_id")
        currentDifficulty = intent.getStringExtra("difficulty")
        currentLevel = intent.getIntExtra("level_number", 0)

        // --- Initialize Boss Manager ---
        val currentBoss = Boss.fromLevel(currentLevel)
        bossManager = BossManager(this, currentBoss)

        // --- Basic UI Setup ---
        if (currentLevel > 0) {
            levelTextView.text = "Level $currentLevel"
            levelTextView.visibility = View.VISIBLE
        } else {
            levelTextView.visibility = View.GONE
        }
        levelTextView.setTextColor(Color.WHITE) // Default color

        val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        isSfxEnabled = sharedPreferences.getBoolean("sfx_enabled", true)
        gridView.isSoundEffectsEnabled = false

        // --- Setup Buttons ---
        val pauseButton: ImageButton = findViewById(R.id.pauseButton)
        pauseButton.setColorFilter(Color.parseColor("#ADD8E6"), PorterDuff.Mode.SRC_IN)
        pauseButton.setOnClickListener { showPauseDialog() }
        playAgainButton.setOnClickListener { restartGame() }

        // --- Load Sounds & Animations ---
        flipSound = MediaPlayer.create(this, R.raw.card_flip)
        winSound = MediaPlayer.create(this, R.raw.win_sound)
        gameOverSound = MediaPlayer.create(this, R.raw.game_over_sound)
        tickingSound = MediaPlayer.create(this, R.raw.ticking_sound)
        winAnimation = AnimationUtils.loadAnimation(this, R.anim.win_animation)
        dialogFadeInAnimation = AnimationUtils.loadAnimation(this, R.anim.dialog_fade_in)

        // --- Configure Level ---
        val uniqueImages = listOf(
            R.drawable.ic_star, R.drawable.ic_heart, R.drawable.ic_moon, R.drawable.ic_sun,
            R.drawable.ic_diamond, R.drawable.ic_club, R.drawable.ic_spade, R.drawable.ic_circle,
            R.drawable.ic_flower, R.drawable.ic_snowflake, R.drawable.ic_lightning, R.drawable.ic_acorn,
            R.drawable.ic_crown, R.drawable.ic_oxo, R.drawable.ic_gem, R.drawable.ic_shield,R.drawable.ic_treasure,
            R.drawable.ic_butterfly, R.drawable.ic_rocket, R.drawable.ic_fires, R.drawable.ic_axe,
            R.drawable.ic_skeleton, R.drawable.ic_sword, R.drawable.ic_ninja, R.drawable.ic_gun
        )

        var numPairs: Int
        var numColumns: Int
        // Determine grid size and time limit based on level or difficulty
        if (currentLevel > 0) {
            when (currentLevel) {
                // Levels 1-10
                1 -> { numPairs = 2; timeLimitSeconds = 0; numColumns = 2 }
                2 -> { numPairs = 3; timeLimitSeconds = 0; numColumns = 2 }
                3 -> { numPairs = 4; timeLimitSeconds = 0; numColumns = 2 }
                4 -> { numPairs = 5; timeLimitSeconds = 0; numColumns = 5 }
                5 -> { numPairs = 6; timeLimitSeconds = 0; numColumns = 3 }
                6 -> { numPairs = 7; timeLimitSeconds = 0; numColumns = 4 }
                7 -> { numPairs = 8; timeLimitSeconds = 0; numColumns = 4 }
                8 -> { numPairs = 9; timeLimitSeconds = 0; numColumns = 3 }
                9 -> { numPairs = 9; timeLimitSeconds = 0; numColumns = 3 }
                10 -> { numPairs = 10; timeLimitSeconds = 0; numColumns = 5 } // LOCKSMITH
                // Levels 11-20
                11 -> { numPairs = 10; timeLimitSeconds = 100; numColumns = 5 }
                12 -> { numPairs = 11; timeLimitSeconds = 90; numColumns = 4 }
                13 -> { numPairs = 11; timeLimitSeconds = 80; numColumns = 4 }
                14 -> { numPairs = 12; timeLimitSeconds = 90; numColumns = 4 }
                15 -> { numPairs = 12; timeLimitSeconds = 80; numColumns = 4 }
                16 -> { numPairs = 13; timeLimitSeconds = 100; numColumns = 4 }
                17 -> { numPairs = 13; timeLimitSeconds = 80; numColumns = 4 }
                18 -> { numPairs = 14; timeLimitSeconds = 100; numColumns = 4 }
                19 -> { numPairs = 14; timeLimitSeconds = 80; numColumns = 4 }
                20 -> { numPairs = 12; timeLimitSeconds = 100; numColumns = 4 } // TRICKSTER
                // Levels 21-30
                21 -> { numPairs = 15; timeLimitSeconds = 90; numColumns = 5 }
                22-> { numPairs = 16; timeLimitSeconds = 100; numColumns = 4 }
                23 -> { numPairs = 16; timeLimitSeconds = 120; numColumns = 6 }
                24 -> { numPairs = 17; timeLimitSeconds = 120; numColumns = 6 }
                25 -> { numPairs = 17; timeLimitSeconds = 100; numColumns = 6 }
                26 -> { numPairs = 18; timeLimitSeconds = 140; numColumns = 6 }
                27 -> { numPairs = 18; timeLimitSeconds = 120; numColumns = 6 }
                28 -> { numPairs = 19; timeLimitSeconds = 140; numColumns = 6 }
                29 -> { numPairs = 19; timeLimitSeconds = 130; numColumns = 6 }
                30 -> { numPairs = 20; timeLimitSeconds = 160; numColumns = 5 } // LYNX
                // Levels 31-36
                31 -> { numPairs = 20; timeLimitSeconds = 150; numColumns = 5 }
                32 -> { numPairs = 21; timeLimitSeconds = 160; numColumns = 6 }
                33 -> { numPairs = 22; timeLimitSeconds = 160; numColumns = 6 }
                34 -> { numPairs = 23; timeLimitSeconds = 160; numColumns = 6 }
                35 -> { numPairs = 24; timeLimitSeconds = 150; numColumns = 8 }
                36 -> { numPairs = 25; timeLimitSeconds = 130; numColumns = 8 } // SHADOW
                else -> { numPairs = 24; timeLimitSeconds = 90; numColumns = 6 }
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

        // --- Dynamic Card Sizing ---
        val numRows = (numPairs * 2 + numColumns - 1) / numColumns
        val cardSize = calculateCardSize(numColumns, numRows)
        gridView.numColumns = numColumns

        // --- Let BossManager set up the level ---
        images = bossManager.setupLevel(levelTextView, uniqueImages, numPairs)

        // --- Setup GridView Adapter ---
        adapter = CardAdapter(this, images, cardSize, bossManager.getLockedPositions(), bossManager.isKeyFound())
        gridView.adapter = adapter
        if (currentLevel == 36) {
            adapter.enableFogOfWar()
        }
        gridView.setOnItemClickListener { _, view, position, _ ->
            // Let the boss manager decide if a click is valid
            if (bossManager.onCardClick(position)) {
                return@setOnItemClickListener
            }

            if (adapter.isCardRevealed(position) || (firstSelected != null && secondSelected != null)) {
                return@setOnItemClickListener
            }

            val flipAnimation = AlphaAnimation(0f, 1f).apply { duration = 200 }
            view.startAnimation(flipAnimation)
            if (isSfxEnabled) flipSound.start()

            onCardFlipped(position)
        }

        // --- Final UI Timer Text ---
        if (timeLimitSeconds > 0) {
            timerTextView.text = "Time Left: ${timeLimitSeconds}s"
        } else {
            timerTextView.text = "No Time Limit"
        }

        // --- Show Tutorial (if any) and Start Game ---
        bossManager.showTutorialAndStartTimers()
    }

    private fun onCardFlipped(position: Int) {
        adapter.revealCard(position)
        bossManager.onCardFlipped(position, images) // Notify boss manager

        if (firstSelected == null) {
            firstSelected = position
        } else {
            secondSelected = position
            checkMatch()
        }
    }

    private fun checkMatch() {
        if (firstSelected != null && secondSelected != null) {
            if (images[firstSelected!!] == images[secondSelected!!]) {
                // It's a match
                bossManager.onMatchFound(firstSelected!!, images) // Notify boss manager
                matchedPositions.add(firstSelected!!)
                matchedPositions.add(secondSelected!!)
                matchedPairs++
                score++
                scoreTextView.text = "Score: $score"

                if (matchedPairs == images.size / 2) {
                    timer?.cancel()
                    if (tickingSound.isPlaying) tickingSound.pause()
                    showWinDialog()
                }
                firstSelected = null
                secondSelected = null
            } else {
                // Not a match, flip back
                gridView.postDelayed({
                    adapter.hideCard(firstSelected!!)
                    adapter.hideCard(secondSelected!!)
                    firstSelected = null
                    secondSelected = null
                }, 500)
            }
        }
    }

    fun startGameTimers() {
        isGameStarted = true
        if (timeLimitSeconds > 0) {
            timeLeftInMillis = (timeLimitSeconds * 1000).toLong()
            startTimer()
        }
        bossManager.startMechanicTimers() // Let boss manager start its own timers
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
                    if (isSfxEnabled && !tickingSound.isPlaying) tickingSound.start()
                } else {
                    timerTextView.setTextColor(Color.WHITE)
                    if (tickingSound.isPlaying) tickingSound.pause()
                }
            }
            override fun onFinish() {
                if (tickingSound.isPlaying) tickingSound.pause()
                showGameOverDialog()
            }
        }.start()
    }

    private fun showWinDialog() {
        timer?.cancel()
        if (isSfxEnabled) winSound.start()
        updateFirestoreData()

        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_win, null)
        builder.setView(customLayout)
        builder.setCancelable(false)
        winDialog = builder.create()

        val konfettiView = customLayout.findViewById<KonfettiView>(R.id.konfettiView)
        val leftBottomParty = Party(
            speed = 0f,
            maxSpeed = 7f,
            damping = 0.99f,
            spread = 50,
            angle = 315,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.35, 0.5),
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100)
        )
        val rightBottomParty = Party(
            speed = 0f,
            maxSpeed = 7f,
            damping = 0.99f,
            spread = 50,
            angle = 225,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.65, 0.5),
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100)
        )
        val topParty = Party(
            speed = 0f,
            maxSpeed = 7f,
            damping = 0.99f,
            spread = 70,
            angle = 270,
            colors = listOf(0xfce18a, 0xff726d, 0xf4306d, 0xb48def, 0x00d4aa),
            position = Position.Relative(0.5, 0.3),
            emitter = Emitter(duration = 500, TimeUnit.MILLISECONDS).max(100)
        )
        konfettiView.start(leftBottomParty, rightBottomParty, topParty)

        val titleText = customLayout.findViewById<TextView>(R.id.dialog_title)
        val messageText = customLayout.findViewById<TextView>(R.id.dialog_message)
        val playAgainButtonDialog = customLayout.findViewById<Button>(R.id.play_again_button)
        val exitButton = customLayout.findViewById<Button>(R.id.exit_button)
        val trophyImage = customLayout.findViewById<ImageView>(R.id.trophy_image)

        trophyImage.startAnimation(winAnimation)

        // Let BossManager customize the dialog first
        val wasCustomized = bossManager.customizeWinDialog(titleText, messageText, playAgainButtonDialog, exitButton, trophyImage)

        if (wasCustomized) {
            // Boss logic for buttons
            if (currentLevel == 36) { // Special case for final boss exit
                exitButton.setOnClickListener {
                    winDialog?.dismiss()
                    startActivity(Intent(this, MainSplashActivity::class.java))
                    finish()
                }
            } else {
                playAgainButtonDialog.setOnClickListener { goToNextLevel() }
                exitButton.setOnClickListener { goToLevelSelection() }
            }
        } else if (currentLevel > 0) {
            // Default logic for normal levels
            messageText.text = "You won with a score of $score!"
            playAgainButtonDialog.text = "NEXT LEVEL"
            playAgainButtonDialog.setOnClickListener { goToNextLevel() }
            exitButton.setOnClickListener { goToLevelSelection() }
        } else {
            // Default logic for challenge mode
            messageText.text = "You won with a score of $score!"
            playAgainButtonDialog.setOnClickListener { restartGame() }
            exitButton.setOnClickListener { finish() }
        }

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

        sfxToggleButton.setOnClickListener {
            isSfxEnabled = !isSfxEnabled
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

    private fun updateFirestoreData() {
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

                    val totalLevels = 36
                    if (currentLevel > 0 && currentLevel > currentHighestLevel && currentLevel < totalLevels) {
                        updateData["highest_level"] = currentLevel + 1 // UNLOCKS NEXT LEVEL
                    }

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
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error getting user document to check high score", e)
                }

            val totalLevels = 36
            if (currentLevel > 0 && currentLevel < totalLevels) {
                val sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
                val highestUnlockedLevel = sharedPreferences.getInt("highest_unlocked_level", 1)

                if (currentLevel >= highestUnlockedLevel) {
                    sharedPreferences.edit().putInt("highest_unlocked_level", currentLevel + 1).apply()
                }
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

    private fun calculateCardSize(columns: Int, rows: Int): Int {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val screenDensity = displayMetrics.density

        val screenWidthDp = (screenWidth / screenDensity).toInt()
        val screenHeightDp = (screenHeight / screenDensity).toInt()

        if (rows > 5 || columns > 6) {
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
            val availableWidth = screenWidthDp - 40
            val cardSizeFromWidth = (availableWidth / columns) - 8
            return when {
                cardSizeFromWidth < 50 -> 50
                cardSizeFromWidth > 80 -> 80
                else -> cardSizeFromWidth
            }
        }
    }

    // --- Navigation & Helper Functions ---
    private fun restartGame() {
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
    private fun goToNextLevel() {
        winDialog?.dismiss()
        val intent = Intent(this, GameActivity::class.java)
        intent.putExtra("player_name", currentPlayerName)
        intent.putExtra("player_id", currentPlayerId)
        intent.putExtra("level_number", currentLevel + 1)
        startActivity(intent)
        finish()
    }
    private fun goToLevelSelection() {
        winDialog?.dismiss()
        val intent = Intent(this, MainSplashActivity::class.java)
        intent.putExtra("show_level_selection", true)
        startActivity(intent)
        finish()
    }


    // --- Activity Lifecycle Methods ---
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
        bossManager.onDestroy() // IMPORTANT: Clean up boss timers
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