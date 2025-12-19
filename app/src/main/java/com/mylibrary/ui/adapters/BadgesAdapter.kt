package com.mylibrary.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mylibrary.R
import com.mylibrary.model.Badge

class BadgesAdapter(
    private val badges: List<Badge>
) : RecyclerView.Adapter<BadgesAdapter.BadgeViewHolder>() {

    class BadgeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val badgeImage: ImageView = view.findViewById(R.id.iv_badge)
        val badgeName: TextView = view.findViewById(R.id.tv_badge_name)
        val progressBar: ProgressBar = view.findViewById(R.id.progress_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        val badge = badges[position]

        holder.badgeName.text = badge.name
        holder.progressBar.progress = badge.progress

        holder.badgeImage.load(badge.imageUrl) {
            placeholder(R.drawable.book_cover_white)
            error(R.drawable.book_cover_white)
        }

        // Gérer la barre de progression
        if (badge.progress >= 100) {
            holder.progressBar.visibility = View.GONE
        } else {
            holder.progressBar.visibility = View.VISIBLE
        }

        // Transparence si pas à 100%
        if (badge.progress < 100) {
            holder.badgeImage.alpha = 0.3f
            holder.badgeName.alpha = 0.5f
        } else {
            holder.badgeImage.alpha = 1.0f
            holder.badgeName.alpha = 1.0f
        }
    }

    override fun getItemCount(): Int = badges.size
}