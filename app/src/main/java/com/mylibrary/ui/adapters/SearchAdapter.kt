package com.mylibrary.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mylibrary.R
import com.mylibrary.model.SearchResult

class SearchAdapter(
    private var dataList: List<SearchResult>,
    private val onResultClicked: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchAdapter.ResultViewHolder>() {

    class ResultViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.result_title)
        val author: TextView = view.findViewById(R.id.result_author)
        val cover: ImageView = view.findViewById(R.id.result_cover_image)
        val details: TextView = view.findViewById(R.id.result_details)  // ✅ AJOUTÉ
        val addButton: Button = view.findViewById(R.id.btn_add_to_library)  // ✅ AJOUTÉ
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result, parent, false)  // ⚠️ Utilisez item_search_result, pas item_search_result_add
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        val result = dataList[position]

        holder.title.text = result.title
        holder.author.text = result.authors
        holder.details.text = result.description  // ✅ AJOUTÉ

        // Chargement de l'image
        if (result.imageUrl != null && result.imageUrl.isNotEmpty()) {
            holder.cover.load(result.imageUrl) {
                crossfade(true)
                error(R.drawable.book_cover_white)
                placeholder(R.drawable.photo_placeholder)
            }
        } else {
            holder.cover.setImageResource(R.drawable.photo_placeholder)
        }

        // Bouton Ajouter à ma bibliothèque
        holder.addButton.setOnClickListener {
            onResultClicked(result)  // ✅ Appelle la fonction de callback avec le livre
        }

    }

    override fun getItemCount() = dataList.size

    fun updateData(newResults: List<SearchResult>) {
        dataList = newResults
        notifyDataSetChanged()
    }
}