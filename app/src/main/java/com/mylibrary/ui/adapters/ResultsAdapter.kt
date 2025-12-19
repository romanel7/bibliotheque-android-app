package com.mylibrary.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mylibrary.R
import com.mylibrary.model.SearchResult
import com.squareup.picasso.Picasso // ⚠️ NÉCESSITE L'AJOUT DE LA LIBRAIRIE PICASSO !

// Définissez une interface pour gérer le clic
class ResultsAdapter(
    private val results: List<SearchResult>,
    private val onResultClicked: (SearchResult) -> Unit // Fonction pour gérer le clic
) : RecyclerView.Adapter<ResultsAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.result_title)
        val author: TextView = view.findViewById(R.id.result_author)

        val cover: ImageView = view.findViewById(R.id.result_cover_image)
        val card: View = view // le layout racine du ViewHolder
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = results[position]
        holder.title.text = result.title
        holder.author.text = result.authors

        // Charger l'image de couverture avec Picasso (si disponible)
        if (result.imageUrl != null) {
            Picasso.get().load(result.imageUrl).into(holder.cover)
        } else {
            // Afficher une image par défaut si l'URL est manquante
            holder.cover.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        // Définir l'action de clic
        holder.card.setOnClickListener {
            onResultClicked(result)
        }
    }

    override fun getItemCount() = results.size
}