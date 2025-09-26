package com.example.firstgame

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat

class LevelAdapter(
    private val context: Context,
    private val levels: List<Int>,
    private val highestUnlockedLevel: Int
) : BaseAdapter() {

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int {
        return levels.size
    }

    override fun getItem(position: Int): Any {
        return levels[position]
    }

    override fun getItemId(position: Int): Long {
        return levels[position].toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = inflater.inflate(R.layout.level_grid_item, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val levelNumber = levels[position]
        viewHolder.levelNumberTextView.text = levelNumber.toString()

        val isUnlocked = levelNumber <= highestUnlockedLevel

        val cardView = viewHolder.levelBackgroundView as? CardView
        if (cardView != null) {
            // --- UPDATED: Use your color names here ---
            val colorRes = if (isUnlocked) R.color.level_unlocked_background else R.color.level_locked_background
            val backgroundColor = ContextCompat.getColor(context, colorRes)
            cardView.setCardBackgroundColor(backgroundColor)
        }

        viewHolder.levelNumberTextView.isEnabled = isUnlocked

        return view
    }

    private class ViewHolder(view: View) {
        val levelBackgroundView: View = view.findViewById(R.id.levelBackgroundView)
        val levelNumberTextView: TextView = view.findViewById(R.id.levelNumberTextView)
    }
}