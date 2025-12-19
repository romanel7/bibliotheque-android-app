package com.mylibrary.ui.bookdetail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.mylibrary.R
import com.mylibrary.databinding.FragmentBookDetailBinding
import com.mylibrary.model.Book
import com.mylibrary.network.BookRepository
import com.mylibrary.network.AiApiManager
import com.google.android.material.chip.Chip
import kotlinx.coroutines.launch
import android.util.Log
import coil.load
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast

class BookDetailFragment : Fragment() {

    private val args: BookDetailFragmentArgs by navArgs()

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var bookRepository: BookRepository
    private lateinit var aiApiManager: AiApiManager

    private var currentBook: Book? = null
    private var summaryGenerated = false  // ✅ NOUVEAU : tracker l'état du résumé

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        bookRepository = BookRepository.getInstance(requireContext())
        aiApiManager = AiApiManager.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configure les boutons
        setupButtons()

        // Bouton fermer
        binding.buttonClose.setOnClickListener {
            Log.d("BookDetail", "Bouton fermer cliqué !")
            findNavController().navigateUp()
        }

        // ✅ NOUVEAU : Listener pour générer le résumé au clic
        binding.cardAiSummary.setOnClickListener {
            if (!summaryGenerated) {
                loadAiSummary(args.bookId)
            }
        }

        // charge les détails du livre
        loadBookDetails(args.bookId)
    }

    private fun loadBookDetails(bookId: Int) {
        lifecycleScope.launch {
            val result = bookRepository.getBookById(bookId)

            result.onSuccess { book ->
                Log.d("BookDetail", "=== LIVRE CHARGÉ ===")
                Log.d("BookDetail", "Titre: ${book.titre}")
                Log.d("BookDetail", "Image URL: ${book.imageUrl}")
                Log.d("BookDetail", "ISBN: ${book.isbn}")
                Log.d("BookDetail", "Publisher: ${book.publisher}")
                Log.d("BookDetail", "Date ajout: ${book.dateAjout}")
                Log.d("BookDetail", "Published date: ${book.publishedDate}")
                Log.d("BookDetail", "Page count: ${book.pageCount}")
                currentBook = book
                displayBookDetails(book)

                // ✅ MODIFIÉ : Vérifier si le résumé existe déjà en BDD
                checkExistingSummary(book)
            }.onFailure { e ->
                // Affichage de l'erreur
                binding.textBookTitle.text = "Erreur"
                binding.textBookAuthor.text = e.message ?: "Livre introuvable."
                binding.cardMemo.visibility = View.GONE
                binding.cardAiSummary.visibility = View.GONE
            }
        }
    }

    private fun displayBookDetails(book: Book) {
        // Remplissage des informations principales
        binding.textBookTitle.text = book.titre
        binding.textBookAuthor.text = "par ${book.auteur}"

        // Image
        val imageUrl = book.imageUrl?.replace("http:", "https:")
        Log.d("BookDetail", "Image URL corrigée: $imageUrl")

        binding.imageBookCover.load(imageUrl) {
            crossfade(true)
            placeholder(R.drawable.photo_placeholder)
            error(R.drawable.book_cover_white)
        }

        // Statut et Note
        binding.textStatus.text = book.status.replaceFirstChar { it.uppercase() }
        book.note?.let { binding.textRating.text = "★ ${it}/5" }

        // Affichage des informations détaillées

        // ISBN
        binding.layoutIsbn.visibility = if (book.isbn.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.textIsbn.text = book.isbn

        // Éditeur
        if (!book.publisher.isNullOrBlank()) {
            Log.d("BookDetail", "Publisher trouvé: ${book.publisher}")
            binding.textPublisher.text = book.publisher
            binding.layoutPublisher.visibility = View.VISIBLE
        } else {
            Log.d("BookDetail", "Publisher est null ou vide")
            binding.layoutPublisher.visibility = View.GONE
        }

        // Langue
        binding.layoutLanguage.visibility = if (book.language.isNullOrEmpty()) View.GONE else View.VISIBLE
        binding.textLanguage.text = book.language

        // Compte de pages
        binding.layoutPageCount.visibility = if (book.pageCount == null) View.GONE else View.VISIBLE
        binding.textPageCount.text = book.pageCount?.toString()

        // Catégories
        binding.chipGroupCategories.removeAllViews()
        if (!book.categories.isNullOrEmpty()) {
            binding.layoutCategories.visibility = View.VISIBLE
            book.categories.forEach { category ->
                val chip = Chip(context).apply {
                    text = category
                    isCheckable = false
                }
                binding.chipGroupCategories.addView(chip)
            }
        } else {
            binding.layoutCategories.visibility = View.GONE
        }

        // Date d'ajout
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)
        try {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .parse(book.dateAjout ?: "")
            binding.textDateAjout.text = date?.let { dateFormat.format(it) } ?: book.dateAjout
            Log.d("BookDetail", "Date formatée: ${dateFormat.format(date)}")
        } catch (e: Exception) {
            Log.e("BookDetail", "Erreur parsing date: ${e.message}")
            binding.textDateAjout.text = book.dateAjout
        }

        // Mémo
        if (!book.memo.isNullOrEmpty()) {
            binding.cardMemo.visibility = View.VISIBLE
            binding.textMemo.text = book.memo
        } else {
            binding.cardMemo.visibility = View.GONE
        }
    }

    // ✅ NOUVELLE FONCTION : Vérifier si le résumé existe déjà
    private fun checkExistingSummary(book: Book) {
        // Pour l'instant, toujours afficher le message d'invitation
        // Le résumé sera généré à la demande
        binding.textSummaryPrompt.visibility = View.VISIBLE
        binding.textAiSummary.visibility = View.GONE
        binding.cardAiSummary.isClickable = true
    }

    // ✅ MODIFIÉE : Générer le résumé au clic
    private fun loadAiSummary(bookId: Int) {
        // Afficher le loading et cacher le texte d'invitation
        binding.textSummaryPrompt.visibility = View.GONE
        binding.progressSummary.visibility = View.VISIBLE
        binding.textAiSummary.visibility = View.GONE

        lifecycleScope.launch {
            val result = aiApiManager.getBookSummary(bookId)

            result.onSuccess { summary ->
                binding.progressSummary.visibility = View.GONE
                binding.textAiSummary.text = summary
                binding.textAiSummary.visibility = View.VISIBLE
                summaryGenerated = true
                binding.cardAiSummary.isClickable = false
                Log.d("BookDetail", "✅ Résumé généré et affiché")
            }

            result.onFailure { error ->
                Log.e("BookDetail", "Erreur résumé: ${error.message}")
                binding.progressSummary.visibility = View.GONE
                binding.textSummaryPrompt.visibility = View.VISIBLE
                binding.textSummaryPrompt.text = "❌ Erreur lors de la génération. Appuyez pour réessayer."
                binding.textAiSummary.visibility = View.GONE
            }
        }
    }

    private fun setupButtons() {
        // Bouton Modifier
        binding.btnEdit.setOnClickListener {
            val action = BookDetailFragmentDirections
                .actionBookDetailFragmentToEditBookFragment(args.bookId)
            findNavController().navigate(action)
        }

        // Bouton Supprimer
        binding.btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Supprimer le livre")
            .setMessage("Êtes-vous sûr de vouloir supprimer ce livre de votre bibliothèque ?")
            .setPositiveButton("Supprimer") { _, _ ->
                deleteBook()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun deleteBook() {
        lifecycleScope.launch {
            val result = bookRepository.deleteBook(args.bookId)

            result.onSuccess {
                Toast.makeText(requireContext(), "Livre supprimé", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }

            result.onFailure { error ->
                Toast.makeText(requireContext(), "Erreur: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}