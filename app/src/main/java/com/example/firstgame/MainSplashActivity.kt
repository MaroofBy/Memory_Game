package com.example.firstgame

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import jp.wasabeef.blurry.Blurry
import java.util.*
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import android.graphics.drawable.InsetDrawable // Add this import at the top of your file


class MainSplashActivity : AppCompatActivity(), NameInputDialogFragment.NameInputListener {


    private lateinit var mainRootLayout: ViewGroup


    private lateinit var welcomeTextView: TextView
    private lateinit var scoreDisplayTextView: TextView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: FirebaseFirestore
    private var currentPlayerName: String? = null
    private var currentPlayerId: String? = null

    // Main Menu Buttons
    private lateinit var tutorialButton: Button
    private lateinit var difficultyButton: Button
    private lateinit var levelsButton: Button
    private lateinit var leaderboardButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var settingsButton: ImageButton

    // Difficulty Selection Buttons
    private lateinit var easyButton: Button
    private lateinit var normalButton: Button
    private lateinit var hardButton: Button
    private lateinit var insaneButton: Button
    private lateinit var impossibleButton: Button

    // Level Selection UI (Pagination)
    private lateinit var levelsLayout: LinearLayout
    private lateinit var levelGrid: GridLayout
    private lateinit var pageLeftArrow: ImageButton
    private lateinit var pageRightArrow: ImageButton
    private lateinit var currentPageTextView: TextView
    private lateinit var gestureDetector: GestureDetectorCompat


    // Pagination Logic
    private val totalLevels = 36
    private val levelsPerPage = 10
    private var currentPage = 1
    private var highestUnlockedLevel: Int = 1

    private var isSfxEnabled = true
    private var settingsDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)


        mainRootLayout = findViewById(R.id.main_root_layout)



        sharedPreferences = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)

        currentPlayerId = sharedPreferences.getString("player_id", null)
        if (currentPlayerId.isNullOrEmpty()) {
            currentPlayerId = UUID.randomUUID().toString()
            sharedPreferences.edit().putString("player_id", currentPlayerId).apply()
        }

        MusicManager.startMusic(this)
        db = FirebaseFirestore.getInstance()
        isSfxEnabled = sharedPreferences.getBoolean("sfx_enabled", true)
        highestUnlockedLevel = sharedPreferences.getInt("highest_unlocked_level", 1)
        // Initialize views
        welcomeTextView = findViewById(R.id.welcomeTextView)
        scoreDisplayTextView = findViewById(R.id.scoreDisplayTextView)
        tutorialButton = findViewById(R.id.tutorialButton)
        difficultyButton = findViewById(R.id.difficultyButton)
        levelsButton = findViewById(R.id.levelsButton)
        leaderboardButton = findViewById(R.id.leaderboardButton)
        backButton = findViewById(R.id.reply)
        settingsButton = findViewById(R.id.settingsButton)
        easyButton = findViewById(R.id.easyButton)
        normalButton = findViewById(R.id.normalButton)
        hardButton = findViewById(R.id.hardButton)
        insaneButton = findViewById(R.id.insaneButton)
        impossibleButton = findViewById(R.id.impossibleButton)
        levelsLayout = findViewById(R.id.levelsLayout)
        levelGrid = findViewById(R.id.levelGrid)
        pageLeftArrow = findViewById(R.id.pageLeftArrow)
        pageRightArrow = findViewById(R.id.pageRightArrow)
        currentPageTextView = findViewById(R.id.currentPageTextView)

        hideAllViews()

        val savedUsername = sharedPreferences.getString("player_name", null)
        val showDifficultySelection = intent.getBooleanExtra("show_difficulty_selection", false)
        val showLevelSelection = intent.getBooleanExtra("show_level_selection", false)

        if (savedUsername.isNullOrEmpty()) {
            showNameInputDialog()
        } else {
            currentPlayerName = savedUsername
            if (showDifficultySelection) {
                showDifficultySelection()
            } else if (showLevelSelection) {
                showLevelSelectionScreen()
            } else {
                showGameMenu()
            }
        }

        // Button Listeners
        settingsButton.setOnClickListener { showSettingsDialog() }
        difficultyButton.setOnClickListener { showDifficultySelection() }
        levelsButton.setOnClickListener { showLevelSelectionScreen() }
        leaderboardButton.setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
        backButton.setOnClickListener { onBackPressed() }
        easyButton.setOnClickListener { startGame("easy") }
        normalButton.setOnClickListener { startGame("normal") }
        hardButton.setOnClickListener { startGame("hard") }
        insaneButton.setOnClickListener { startGame("insane") }
        impossibleButton.setOnClickListener { startGame("impossible") }
        tutorialButton.setOnClickListener {
            startActivity(Intent(this, TutorialActivity::class.java))
        }

        // Pagination Arrows
        pageLeftArrow.setOnClickListener {
            if (currentPage > 1) {
                currentPage--
                displayLevelButtons()
            }
        }
        pageRightArrow.setOnClickListener {
            val maxPage = (totalLevels + levelsPerPage - 1) / levelsPerPage
            if (currentPage < maxPage) {
                currentPage++
                displayLevelButtons()
            }
        }
        levelsLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        gestureDetector = GestureDetectorCompat(this, SwipeGestureListener())

    }
    private inner class SwipeGestureListener : GestureDetector.SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (e1 == null) return false

            val diffX = e2.x - e1.x
            val diffY = e2.y - e1.y

            if (Math.abs(diffX) > Math.abs(diffY)) {
                if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // Swipe right - go to previous page
                        if (currentPage > 1) {
                            currentPage--
                            displayLevelButtons()
                        }
                    } else {
                        // Swipe left - go to next page
                        val maxPage = (totalLevels + levelsPerPage - 1) / levelsPerPage
                        if (currentPage < maxPage) {
                            currentPage++
                            displayLevelButtons()
                        }
                    }
                    return true
                }
            }
            return false
        }
    }

    override fun onResume() {
        super.onResume()
        MusicManager.resumeMusic()
        loadHighestScore()
    }

    override fun onPause() {
        super.onPause()
        MusicManager.pauseMusic()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            MusicManager.stopAndReleaseMusic()
        }
    }

    private fun hideAllViews() {
        welcomeTextView.visibility = View.GONE
        scoreDisplayTextView.visibility = View.GONE
        tutorialButton.visibility = View.GONE
        difficultyButton.visibility = View.GONE
        levelsButton.visibility = View.GONE
        leaderboardButton.visibility = View.GONE
        backButton.visibility = View.GONE
        settingsButton.visibility = View.GONE
        easyButton.visibility = View.GONE
        normalButton.visibility = View.GONE
        hardButton.visibility = View.GONE
        insaneButton.visibility = View.GONE
        impossibleButton.visibility = View.GONE
        levelsLayout.visibility = View.GONE
    }

    private fun showGameMenu() {
        hideAllViews()
        welcomeTextView.text = "Welcome, ${currentPlayerName}!"
        welcomeTextView.visibility = View.VISIBLE
        scoreDisplayTextView.visibility = View.VISIBLE
        settingsButton.visibility = View.VISIBLE
        difficultyButton.visibility = View.VISIBLE
        levelsButton.visibility = View.VISIBLE
        tutorialButton.visibility = View.VISIBLE
        leaderboardButton.visibility = View.VISIBLE
    }

    private fun showDifficultySelection() {
        hideAllViews()
        welcomeTextView.text = "Select Difficulty"
        welcomeTextView.visibility = View.VISIBLE
        easyButton.visibility = View.VISIBLE
        normalButton.visibility = View.VISIBLE
        hardButton.visibility = View.VISIBLE
        insaneButton.visibility = View.VISIBLE
        impossibleButton.visibility = View.VISIBLE
        backButton.visibility = View.VISIBLE
    }

    private fun showLevelSelectionScreen() {
        hideAllViews()
        welcomeTextView.text = "Select a Level"
        welcomeTextView.visibility = View.VISIBLE
        backButton.visibility = View.VISIBLE
        levelsLayout.visibility = View.VISIBLE
        displayLevelButtons()
    }

    private fun displayLevelButtons() {
        levelGrid.removeAllViews()

        val maxPage = (totalLevels + levelsPerPage - 1) / levelsPerPage
        val startLevel = (currentPage - 1) * levelsPerPage + 1
        val endLevel = minOf(startLevel + levelsPerPage - 1, totalLevels)

        currentPageTextView.text = "Levels $startLevel-$endLevel"
        pageLeftArrow.visibility = if (currentPage > 1) View.VISIBLE else View.INVISIBLE
        pageRightArrow.visibility = if (currentPage < maxPage) View.VISIBLE else View.INVISIBLE

        // --- CHANGE 1: SMALLER BUTTONS & MORE SIDE SPACE ---
        // We now use 75% of the screen width. You can adjust 0.75f to make it bigger or smaller.
        val screenWidth = resources.displayMetrics.widthPixels
        val spacing = (30 * resources.displayMetrics.density).toInt()

        val buttonSize = ((screenWidth * 0.90f).toInt() - (spacing * (3 + 1))) / 3

        // --- CHANGE 2: FIXING THE ARROW BUG ---
        // We force the grid to always have a height of 4 rows, so it doesn't shrink on the last page.
        val totalGridHeight = (buttonSize * 4) + (spacing * 5) // 4 rows of buttons + 5 rows of spacing
        levelGrid.layoutParams.height = totalGridHeight

        val hasCenteredButton = (endLevel % 10 == 0 && endLevel <= 30)

        val gridLevelsEnd = if (hasCenteredButton) endLevel - 1 else endLevel
        for (levelNumber in startLevel..gridLevelsEnd) {
            addLevelButtonToGrid(levelNumber, buttonSize, spacing)
        }

        if (hasCenteredButton) {
            val spacer = Space(this)
            levelGrid.addView(spacer)
            addLevelButtonToGrid(endLevel, buttonSize, spacing)
        }
    }

    private fun addLevelButtonToGrid(levelNumber: Int, buttonSize: Int, spacing: Int) {
        val isUnlocked = levelNumber <= highestUnlockedLevel
        val levelButton = Button(this, null, 0, R.style.LevelNumberButton)

        when (levelNumber) {
            10 -> {
                levelButton.text = ""
                levelButton.setBackgroundResource(if (isUnlocked) R.drawable.unkc_lockdown_level else R.drawable.lck_lockdown_level)
            }
            20 -> {
                levelButton.text = ""
                levelButton.setBackgroundResource(if (isUnlocked) R.drawable.unkc_chaos_level else R.drawable.lkc_chaos_level)
            }
            30 -> {
                levelButton.text = ""
                levelButton.setBackgroundResource(if (isUnlocked) R.drawable.boss_unlocked else R.drawable.boss_locked)
            }
            36 -> {
                levelButton.text = ""
                levelButton.setBackgroundResource(if (isUnlocked) R.drawable.unkc_bomb_squad_level else R.drawable.lkc_bomb_squad_level)
            }
            else -> {
                levelButton.text = levelNumber.toString()
                levelButton.setBackgroundResource(if (isUnlocked) R.drawable.level_unlocked_btn_background else R.drawable.level_locked_btn_background)
            }
        }

        levelButton.isEnabled = isUnlocked
        levelButton.setOnTouchListener { _, event ->
            val gestureHandled = gestureDetector.onTouchEvent(event)
            if (!gestureHandled && event.action == MotionEvent.ACTION_UP) {
                if (isUnlocked) {
                    startGame(levelNumber)
                }
            }
            true
        }

        val params = GridLayout.LayoutParams()
        params.width = buttonSize
        params.height = buttonSize

        // --- CHANGE 3: REVERTING THE VERTICAL SPACING ---
        // We are back to the original, equal spacing on all sides.
        params.setMargins(spacing / 2, spacing / 2, spacing / 2, spacing / 2)

        levelButton.layoutParams = params
        levelGrid.addView(levelButton)
    }

    private fun showNameInputDialog() {
        NameInputDialogFragment().show(supportFragmentManager, "NameInputDialog")
    }

    override fun onNameSubmitted(playerName: String) {
        sharedPreferences.edit().putString("player_name", playerName).apply()
        currentPlayerName = playerName
        saveInitialUserDataToFirestore()
        showGameMenu()
    }

    private fun saveInitialUserDataToFirestore() {
        val playerId = sharedPreferences.getString("player_id", null) ?: return
        val playerName = sharedPreferences.getString("player_name", "Anonymous") ?: "Anonymous"
        val userRef = db.collection("users").document(playerId)

        val initialUserData = hashMapOf(
            "Name" to playerName,
            "score" to 0L,
            "highest_level" to 1,
            "challengeMode" to "none",
            "best_time" to 9223372036854775807L
        )

        userRef.set(initialUserData)
            .addOnSuccessListener { Log.d("Firestore", "Initial player '$playerName' data saved with score 0.") }
            .addOnFailureListener { e -> Log.w("Firestore", "Error saving initial player data for '$playerName'", e) }
    }

    private fun loadHighestScore() {
        val playerId = sharedPreferences.getString("player_id", null) ?: return
        db.collection("users").document(playerId).get()
            .addOnSuccessListener { documentSnapshot ->
                scoreDisplayTextView.text = "Highest Score: ${documentSnapshot.getLong("score") ?: 0L}"
            }
            .addOnFailureListener { e ->
                scoreDisplayTextView.text = "Highest Score: Error"
                Log.e("Firestore", "Error loading score: ${e.message}", e)
            }
    }

    private fun startGame(difficulty: String) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("difficulty", difficulty)
            putExtra("player_name", currentPlayerName)
            putExtra("player_id", currentPlayerId)
        }
        startActivity(intent)
    }

    private fun startGame(level: Int) {
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("level_number", level)
            putExtra("player_name", currentPlayerName)
            putExtra("player_id", currentPlayerId)
        }
        startActivity(intent)
    }

    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_settings, null)
        builder.setView(customLayout)

        builder.setCancelable(true)
        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        settingsDialog = dialog

        val musicToggleButton = customLayout.findViewById<ImageButton>(R.id.musicToggleButton2)
        val sfxToggleButton = customLayout.findViewById<ImageButton>(R.id.sfxToggleButton2)
        val reportBugButton = customLayout.findViewById<Button>(R.id.reportBugButton)
        val closeButton = customLayout.findViewById<ImageButton>(R.id.closeButton)

        val aboutButton = customLayout.findViewById<Button>(R.id.aboutButton)

        updateMusicToggleButtonIcon(musicToggleButton)
        updateSfxToggleButtonIcon(sfxToggleButton)

        musicToggleButton.setOnClickListener {
            MusicManager.toggleMusic()
            updateMusicToggleButtonIcon(musicToggleButton)
        }

        sfxToggleButton.setOnClickListener {
            isSfxEnabled = !isSfxEnabled
            with(sharedPreferences.edit()) {
                putBoolean("sfx_enabled", isSfxEnabled)
                apply()
            }
            updateSfxToggleButtonIcon(sfxToggleButton)
        }

        reportBugButton.setOnClickListener {
            showReportBugDialog()
            settingsDialog?.dismiss()
        }

        // --- NEW CODE: Set listener for the About button ---
        aboutButton.setOnClickListener {
            showAboutDialog()
            settingsDialog?.dismiss()
        }

        closeButton.setOnClickListener {
            settingsDialog?.dismiss()
        }

        settingsDialog?.show()
    }

    private fun showAboutDialog() {
        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_about, null)
        builder.setView(customLayout)

        val dialog = builder.create()
        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation

        dialog.setOnShowListener {
            Blurry.with(this)
                .radius(15) // You can adjust these values
                .sampling(6) // A higher sampling is faster
                .onto(findViewById(android.R.id.content))
        }
        dialog.setOnDismissListener {
            Blurry.delete(findViewById(android.R.id.content))
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val closeButton = customLayout.findViewById<Button>(R.id.aboutCloseButton)
        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateMusicToggleButtonIcon(button: ImageButton) {
        if (MusicManager.isMusicPlaying) {
            button.setImageResource(R.drawable.ic_music_on)
        } else {
            button.setImageResource(R.drawable.ic_music_off)
        }
    }

    private fun updateSfxToggleButtonIcon(button: ImageButton) {
        // Always read fresh from SharedPreferences
        val currentSfxState = sharedPreferences.getBoolean("sfx_enabled", true)
        isSfxEnabled = currentSfxState // Sync the local variable

        if (currentSfxState) {
            button.setImageResource(R.drawable.ic_sfx_on)
        } else {
            button.setImageResource(R.drawable.ic_sfx_off)
        }
    }

    private fun showReportBugDialog() {
        val builder = AlertDialog.Builder(this)
        val customLayout = layoutInflater.inflate(R.layout.dialog_report_bug, null)
        builder.setView(customLayout)

        val dialog = builder.create()
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val inputField = customLayout.findViewById<EditText>(R.id.report_input)
        val sendButton = customLayout.findViewById<Button>(R.id.send_report_button)

        sendButton.setOnClickListener {
            val reportText = inputField.text.toString().trim()

            if (reportText.isNotEmpty()) {
                val playerId = sharedPreferences.getString("player_id", null)

                if (playerId != null) {
                    val userReportRef = db.collection("bug_reports").document(playerId)

                    val newReport = hashMapOf(
                        "reportText" to reportText,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    userReportRef.get()
                        .addOnSuccessListener { documentSnapshot ->
                            if (documentSnapshot.exists()) {
                                userReportRef.update("reports", FieldValue.arrayUnion(newReport))
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed to send report: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            } else {
                                val reportData = hashMapOf(
                                    "playerName" to (currentPlayerName ?: "Anonymous"),
                                    "reports" to FieldValue.arrayUnion(newReport)
                                )
                                userReportRef.set(reportData)
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                                        dialog.dismiss()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Failed to send report: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                } else {
                    Toast.makeText(this, "Player ID not found. Please try restarting the game.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Please write something before sending.", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    override fun onBackPressed() {
        if (levelsLayout.visibility == View.VISIBLE || easyButton.visibility == View.VISIBLE) {
            showGameMenu()
        } else {
            super.onBackPressed()
        }
    }
}