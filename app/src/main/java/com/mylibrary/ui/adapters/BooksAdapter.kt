package com.mylibrary.ui.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.mylibrary.R
import com.mylibrary.model.Book

/**
 * Adapter générique pour afficher la liste des livres.
 * Accepte une lambda pour gérer le clic sur un livre.
 */
class BooksAdapter(
    private val books: List<Book>,
    private val onBookClick: (Int) -> Unit
) : RecyclerView.Adapter<BooksAdapter.BookViewHolder>() {

    class BookViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.book_cover)
        val title: TextView = view.findViewById(R.id.book_title)
        val author: TextView = view.findViewById(R.id.book_author)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_book_card, parent, false)
        return BookViewHolder(view)
    }

    override fun onBindViewHolder(holder: BookViewHolder, position: Int) {
        val book = books[position]

        holder.title.text = book.titre
        holder.author.text = book.auteur

        Log.d("BooksAdapter", "Livre: ${book.titre} - Image URL: ${book.imageUrl}")

        val imageUrl = book.imageUrl

        if (imageUrl != null && imageUrl.isNotEmpty()) {
            holder.cover.load(imageUrl) {
                crossfade(true)
                error(R.drawable.book_cover_white)
                placeholder(R.drawable.photo_placeholder)
            }
        } else {
            holder.cover.setImageResource(R.drawable.photo_placeholder)
        }

        holder.itemView.setOnClickListener {
            book.id?.let { bookId ->
                onBookClick(bookId)
            } ?: Log.e("BooksAdapter", "Erreur: ID du livre est null")
        }
    }

    override fun getItemCount() = books.size
}