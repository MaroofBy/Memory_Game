package com.example.firstgame

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.concurrent.TimeUnit
import com.google.firebase.firestore.DocumentSnapshot

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var leaderboardListView: ListView
    private lateinit var tabHighestLevel: Button
    private lateinit var tabChallengeMode: Button
    private lateinit var youEntryContainer: CardView
    private lateinit var yourRankTextView: TextView
    private lateinit var yourNameTextView: TextView
    private lateinit var yourExtraTextView1: TextView
    private lateinit var yourExtraTextView2: TextView

    // Header views
    private lateinit var headerRank: TextView
    private lateinit var headerName: TextView
    private lateinit var headerExtra1: TextView
    private lateinit var headerExtra2: TextView

    private val db = FirebaseFirestore.getInstance()
    private var currentPlayerName: String? = null
    private var currentPlayerId: String? = null
    private var showingHighestLevel = true

    // A predefined order of difficulties for correct sorting
    private val difficultyOrder = listOf("impossible", "insane", "hard", "normal", "easy")

    // Use an enum to correctly pass the leaderboard type to the adapter
    enum class LeaderboardType { HIGHEST_LEVEL, CHALLENGE_MODE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        leaderboardListView = findViewById(R.id.leaderboardListView)
        tabHighestLevel = findViewById(R.id.tabHighestLevel)
        tabChallengeMode = findViewById(R.id.tabChallengeMode)

        // "You" entry
        youEntryContainer = findViewById(R.id.youEntryContainer)
        yourRankTextView = findViewById(R.id.yourRankTextView)
        yourNameTextView = findViewById(R.id.yourNameTextView)
        yourExtraTextView1 = findViewById(R.id.yourExtraTextView1)
        yourExtraTextView2 = findViewById(R.id.yourExtraTextView2)

        // Header views
        headerRank = findViewById(R.id.headerRank)
        headerName = findViewById(R.id.headerName)
        headerExtra1 = findViewById(R.id.headerExtra1)
        headerExtra2 = findViewById(R.id.headerExtra2)

        // Load current player's name and ID from SharedPreferences
        val sharedPrefs = getSharedPreferences("game_prefs", Context.MODE_PRIVATE)
        currentPlayerName = sharedPrefs.getString("player_name", null)
        currentPlayerId = sharedPrefs.getString("player_id", null)

        // Back button
        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }




        // Tab switching
        tabHighestLevel.setOnClickListener {
            showingHighestLevel = true
            loadLeaderboard()
            highlightTab()
            updateLeaderboardHeader()
        }
        tabChallengeMode.setOnClickListener {
            showingHighestLevel = false
            loadLeaderboard()
            highlightTab()
            updateLeaderboardHeader()
        }

        // Initial load
        highlightTab()
        updateLeaderboardHeader()
        loadLeaderboard()
    }

    private fun highlightTab() {
        val purple500 = ContextCompat.getColor(this, R.color.purple_500)
        val teal700 = ContextCompat.getColor(this, R.color.teal_700)

        if (showingHighestLevel) {
            tabHighestLevel.setBackgroundColor(purple500)
            tabHighestLevel.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            tabChallengeMode.setBackgroundColor(teal700)
            tabChallengeMode.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        } else {
            tabHighestLevel.setBackgroundColor(teal700)
            tabHighestLevel.setTextColor(ContextCompat.getColor(this, android.R.color.white))
            tabChallengeMode.setBackgroundColor(purple500)
            tabChallengeMode.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        }
    }

    private fun updateLeaderboardHeader() {
        if (showingHighestLevel) {
            headerExtra1.text = "Highest Lvl"
            headerExtra2.visibility = View.GONE
        } else {
            headerExtra1.text = "Difficulty"
            headerExtra2.visibility = View.VISIBLE
            headerExtra2.text = "Time"
        }
    }


    override fun onResume() {
        super.onResume()
        MusicManager.resumeMusic()
    }

    override fun onPause() {
        super.onPause()
        MusicManager.pauseMusic()
    }

    private fun loadLeaderboard() {
        if (showingHighestLevel) {
            loadHighestLevelLeaderboard()
            loadCurrentPlayerHighestLevel()
        } else {
            loadChallengeModeLeaderboard()
            loadCurrentPlayerChallengeMode()
        }
    }

    /** Load Highest Level Leaderboard (all entries, scrollable) **/
    private fun loadHighestLevelLeaderboard() {
        db.collection("users")
            .orderBy("highest_level", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val leaderboardEntries = mutableListOf<LeaderboardEntry>()
                var rank = 1
                for (document in querySnapshot.documents) {
                    val name = document.getString("Name") ?: "Anonymous"
                    val level = document.getLong("highest_level") ?: 0L
                    val playerId = document.id // Get the unique ID from the document

                    // Check for the unique ID, not the name
                    val displayName = if (playerId == currentPlayerId) "$name (You)" else name

                    leaderboardEntries.add(
                        LeaderboardEntry(rank, displayName, extra1 = level.toString(), extra2 = "")
                    )
                    rank++
                }

                leaderboardListView.adapter = LeaderboardAdapter(
                    this,
                    leaderboardEntries,
                    LeaderboardType.HIGHEST_LEVEL
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
                Log.e("Leaderboard", "Error: ", e)
            }
    }

    /** Load Challenge Mode Leaderboard (all entries, scrollable) **/
    private fun loadChallengeModeLeaderboard() {
        db.collection("users").get()
            .addOnSuccessListener { querySnapshot ->
                val allChallengeEntries = querySnapshot.documents.mapNotNull { document ->
                    val name = document.getString("Name") ?: return@mapNotNull null
                    val difficulty = document.getString("challengeMode") ?: return@mapNotNull null
                    val bestTime = document.getLong("best_time") ?: return@mapNotNull null
                    val playerId = document.id

                    if (difficulty == "none" || bestTime <= 0) return@mapNotNull null

                    val displayName = if (playerId == currentPlayerId) "$name (You)" else name
                    LeaderboardEntry(0, displayName, difficulty, bestTime.toString())
                }.sortedWith(compareBy<LeaderboardEntry> {
                    difficultyOrder.indexOf(it.extra1.lowercase())
                }.thenBy {
                    it.extra2.toLongOrNull() ?: Long.MAX_VALUE
                })

                val rankedEntries = allChallengeEntries.mapIndexed { index, entry ->
                    entry.copy(rank = index + 1)
                }

                leaderboardListView.adapter = LeaderboardAdapter(
                    this,
                    rankedEntries,
                    LeaderboardType.CHALLENGE_MODE
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load challenge mode", Toast.LENGTH_SHORT).show()
                Log.e("Leaderboard", "Error: ", e)
            }
    }

    private fun loadCurrentPlayerHighestLevel() {
        currentPlayerId?.let { id ->
            db.collection("users").orderBy("highest_level", Query.Direction.DESCENDING).get()
                .addOnSuccessListener { querySnapshot ->
                    val allEntries = querySnapshot.documents.mapNotNull { document ->
                        val name = document.getString("Name") ?: return@mapNotNull null
                        val level = document.getLong("highest_level") ?: 0L
                        val playerId = document.id
                        LeaderboardEntryWithId(0, name, "Level", level.toString(), playerId)
                    }

                    val rank = allEntries.indexOfFirst { it.id == id } + 1
                    val playerEntry = allEntries.find { it.id == id }

                    if (playerEntry != null && rank > 0) {
                        youEntryContainer.visibility = View.VISIBLE
                        yourNameTextView.text = playerEntry.name
                        yourRankTextView.text = "${rank}."

                        // Match adapter: left invisible, right = level
                        yourExtraTextView1.visibility = View.INVISIBLE
                        yourExtraTextView1.text = ""
                        yourExtraTextView2.visibility = View.VISIBLE
                        yourExtraTextView2.text = playerEntry.extra2
                    } else {
                        youEntryContainer.visibility = View.GONE
                    }

                }
                .addOnFailureListener { e ->
                    Log.e("Leaderboard", "Error loading player entry", e)
                    youEntryContainer.visibility = View.GONE
                }
        }
    }

    private fun loadCurrentPlayerChallengeMode() {
        currentPlayerId?.let { id ->
            db.collection("users").get()
                .addOnSuccessListener { querySnapshot ->
                    val allEntries = querySnapshot.documents.mapNotNull { document ->
                        val name = document.getString("Name") ?: return@mapNotNull null
                        val difficulty = document.getString("challengeMode") ?: return@mapNotNull null
                        val bestTime = document.getLong("best_time") ?: return@mapNotNull null
                        val playerId = document.id

                        if (difficulty == "none" || bestTime <= 0) return@mapNotNull null

                        LeaderboardEntryWithId(0, name, difficulty, bestTime.toString(), playerId)
                    }.sortedWith(compareBy<LeaderboardEntryWithId> {
                        difficultyOrder.indexOf(it.extra1.lowercase())
                    }.thenBy {
                        it.extra2.toLongOrNull() ?: Long.MAX_VALUE
                    })

                    val playerEntry = allEntries.find { it.id == id }
                    if (playerEntry != null) {
                        val rank = allEntries.indexOfFirst { it.id == id } + 1
                        youEntryContainer.visibility = View.VISIBLE
                        yourNameTextView.text = playerEntry.name
                        yourRankTextView.text = "${rank}."

                        // Both visible: difficulty + time
                        yourExtraTextView1.visibility = View.VISIBLE
                        yourExtraTextView1.text = playerEntry.extra1.uppercase()
                        yourExtraTextView2.visibility = View.VISIBLE
                        val timeInMs = playerEntry.extra2.toLongOrNull() ?: 0
                        val seconds = timeInMs / 1000
                        val millis = (timeInMs % 1000) / 10 // 🔹 two digits (0–99)
                        yourExtraTextView2.text = "${seconds}s ${String.format("%02d", millis)}ms"

                    } else {
                        // 🔹 Show message instead of hiding
                        youEntryContainer.visibility = View.VISIBLE
                        yourRankTextView.text = ""
                        yourNameTextView.text = "You haven't played Challenge mode yet."
                        yourExtraTextView1.visibility = View.GONE
                        yourExtraTextView2.visibility = View.GONE
                    }


                }
                .addOnFailureListener { e ->
                    Log.e("Leaderboard", "Error loading player entry", e)
                    youEntryContainer.visibility = View.GONE
                }
        }
    }

    /** Entry Model **/
    data class LeaderboardEntry(
        val rank: Int,
        val name: String,
        val extra1: String,
        val extra2: String
    )

    data class LeaderboardEntryWithId(
        val rank: Int,
        val name: String,
        val extra1: String,
        val extra2: String,
        val id: String
    )

    /** Adapter **/
    private class LeaderboardAdapter(
        private val context: Context,
        private val entries: List<LeaderboardEntry>,
        private val type: LeaderboardType
    ) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)

        override fun getCount(): Int = entries.size
        override fun getItem(position: Int): Any = entries[position]
        override fun getItemId(position: Int): Long = position.toLong()

        private class ViewHolder(view: View) {
            val rankTextView: TextView = view.findViewById(R.id.rankTextView)
            val medalImageView: ImageView = view.findViewById(R.id.medalImageView)
            val nameTextView: TextView = view.findViewById(R.id.nameTextView)
            val extraTextView1: TextView = view.findViewById(R.id.extraTextView1)
            val extraTextView2: TextView = view.findViewById(R.id.extraTextView2)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view: View
            val holder: ViewHolder
            if (convertView == null) {
                view = inflater.inflate(R.layout.list_item_leaderboard, parent, false)
                holder = ViewHolder(view)
                view.tag = holder
            } else {
                view = convertView
                holder = view.tag as ViewHolder
            }

            val entry = entries[position]

            // 🔹 Default: show rank number
            holder.rankTextView.visibility = View.VISIBLE
            holder.rankTextView.text = "${entry.rank}."
            holder.medalImageView.visibility = View.GONE

            // 🔹 Override for top 3 with medals
            when (entry.rank) {
                1 -> {
                    holder.rankTextView.visibility = View.GONE
                    holder.medalImageView.visibility = View.VISIBLE
                    holder.medalImageView.setImageResource(R.drawable.ic_gold_medal)
                }
                2 -> {
                    holder.rankTextView.visibility = View.GONE
                    holder.medalImageView.visibility = View.VISIBLE
                    holder.medalImageView.setImageResource(R.drawable.ic_silver_medal)
                }
                3 -> {
                    holder.rankTextView.visibility = View.GONE
                    holder.medalImageView.visibility = View.VISIBLE
                    holder.medalImageView.setImageResource(R.drawable.ic_bronze_medal)
                }
            }

            // 🔹 Set player name
            holder.nameTextView.text = entry.name

            // 🔹 Depending on type, show highest level or challenge time
            // 🔹 Depending on type, show highest level or challenge time
            if (type == LeaderboardType.HIGHEST_LEVEL) {
                // Keep left column invisible, put level in right column
                holder.extraTextView1.visibility = View.INVISIBLE
                holder.extraTextView1.text = ""
                holder.extraTextView2.visibility = View.VISIBLE
                holder.extraTextView2.text = entry.extra1 // level
            } else {
                // Both visible: difficulty + time
                holder.extraTextView1.visibility = View.VISIBLE
                holder.extraTextView1.text = entry.extra1.uppercase()
                holder.extraTextView2.visibility = View.VISIBLE

                val timeInMs = entry.extra2.toLongOrNull() ?: 0
                val seconds = timeInMs / 1000
                val millis = (timeInMs % 1000) / 10 // 🔹 two digits (0–99)
                holder.extraTextView2.text = "${seconds}s ${String.format("%02d", millis)}ms"
            }



            val animation = AnimationUtils.loadAnimation(context, R.anim.slide_up)
            view.startAnimation(animation)

            return view
        }
    }

}
