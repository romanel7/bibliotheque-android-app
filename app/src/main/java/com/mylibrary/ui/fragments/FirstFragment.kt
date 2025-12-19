package com.mylibrary.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mylibrary.R
import com.mylibrary.databinding.FragmentFirstBinding
import com.mylibrary.model.Book
import com.mylibrary.network.AiApiManager
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager
import com.mylibrary.ui.adapters.BooksAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch


class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null
    private val binding get() = _binding!!
    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository
    private lateinit var aiApiManager: AiApiManager

    // Cache pour éviter de recharger à chaque fois
    private var cachedRecommendations: List<Book>? = null
    private var isLoadingRecommendations = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())
        aiApiManager = AiApiManager.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.header_title).text = "Pour vous"

        // NAVIGATION FOOTER
        val navBar = binding.root.findViewById<View>(R.id.navigation_bar)

        navBar.findViewById<View>(R.id.btn_add).setOnClickListener {
            Log.d("NavigationBar", "=== BOUTON + CLIQUÉ ===")
            findNavController().navigate(R.id.action_FirstFragment_to_SecondFragment)
        }

        navBar.findViewById<View>(R.id.btn_home).setOnClickListener {
            // Reste sur FirstFragment
        }

        navBar.findViewById<View>(R.id.btn_search).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_SearchFragment)
        }

        navBar.findViewById<View>(R.id.btn_account).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_AccountFragment)
        }

        navBar.findViewById<View>(R.id.btn_library).setOnClickListener {
            findNavController().navigate(R.id.action_FirstFragment_to_BibliothequeFragment)
        }

        chargerLivres()
    }

    override fun onResume() {
        super.onResume()
        // Ne recharger QUE les livres, pas les recommandations
        chargerLivres()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun chargerLivres() {
        if (!connectionManager.isLoggedIn()) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        // Utiliser lifecycleScope (du Fragment, pas de la vue)
        lifecycleScope.launch {
            try {
                val resultLivres = bookRepository.getAllMyBooks()

                resultLivres.onSuccess { livres ->
                    // ✅ Vérifier que binding existe
                    if (_binding == null) {
                        Log.w("FirstFragment", "⚠️ Binding null, fragment détruit")
                        return@onSuccess
                    }

                    afficherLivres(livres)

                    // Vérifier si l'utilisateur a des livres lus
                    val livresLus = livres.filter { it.status == "lu" }

                    if (livresLus.isEmpty()) {
                        // Pas de livres lus → message d'encouragement
                        cachedRecommendations = null
                        if (_binding != null) {
                            setupRecyclerView(
                                binding.root.findViewById(R.id.section_recommendations),
                                emptyList(),
                                "🤖 Recommandé pour vous"
                            )
                        }
                    } else {
                        // Afficher les recommandations en cache OU charger
                        if (cachedRecommendations != null) {
                            Log.d("FirstFragment", "📦 Utilisation du cache (${cachedRecommendations!!.size} recommandations)")
                            if (_binding != null) {
                                setupRecyclerView(
                                    binding.root.findViewById(R.id.section_recommendations),
                                    cachedRecommendations!!,
                                    "🤖 Recommandé pour vous"
                                )
                            }
                        } else {
                            chargerRecommendations()
                        }
                    }
                }

                resultLivres.onFailure { error ->
                    if (_binding != null) {
                        Toast.makeText(context, "Erreur: ${error.message}", Toast.LENGTH_SHORT).show()

                        if (error.message?.contains("401") == true || error.message?.contains("403") == true) {
                            connectionManager.clearToken()
                            findNavController().navigate(R.id.loginFragment)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirstFragment", "💥 Exception chargerLivres: ${e.message}", e)
            }
        }
    }

    // ==========================================
    // CHARGER LES RECOMMANDATIONS (optimisé avec async)
    // ==========================================
// ==========================================
// CHARGER LES RECOMMANDATIONS (avec limite d'appels simultanés)
// ==========================================
    private fun chargerRecommendations() {
        // Éviter les appels multiples simultanés
        if (isLoadingRecommendations) {
            Log.d("FirstFragment", "⏳ Chargement déjà en cours...")
            return
        }

        isLoadingRecommendations = true
        Log.d("FirstFragment", "🔵 Début chargement recommandations...")

        lifecycleScope.launch {
            try {
                val result = aiApiManager.getRecommendations()

                result.onSuccess { recommendations ->
                    Log.d("FirstFragment", "📥 Reçu ${recommendations.size} recommandations de l'IA")

                    // Enrichir les recommandations par lots de 2 pour éviter de surcharger Google Books
                    val enrichedBooks = mutableListOf<Book?>()

                    for (i in recommendations.indices step 2) {
                        val batch = recommendations.subList(i, minOf(i + 2, recommendations.size))

                        val batchResults = batch.mapIndexed { offset, rec ->
                            async {
                                enrichRecommendation(i + offset, rec)
                            }
                        }.awaitAll()

                        enrichedBooks.addAll(batchResults)

                        // Petit délai entre les lots pour éviter de surcharger l'API
                        if (i + 2 < recommendations.size) {
                            kotlinx.coroutines.delay(300)
                        }
                    }

                    // ✅ Vérifier que binding existe
                    if (_binding == null) {
                        Log.w("FirstFragment", "⚠️ Binding null après enrichissement")
                        return@onSuccess
                    }

                    // Filtrer les nulls (en cas d'erreur)
                    val validBooks = enrichedBooks.filterNotNull()

                    Log.d("FirstFragment", "📊 Livres enrichis: ${validBooks.size}")
                    validBooks.forEachIndexed { index, book ->
                        Log.d("FirstFragment", "   [$index] ${book.titre} - Image: ${book.imageUrl?.take(50)}")
                    }

                    // Mettre en cache
                    cachedRecommendations = validBooks

                    // Afficher les recommandations enrichies
                    if (_binding != null) {
                        setupRecyclerView(
                            binding.root.findViewById(R.id.section_recommendations),
                            validBooks,
                            "🤖 Recommandé pour vous"
                        )
                    }
                }

                result.onFailure { error ->
                    Log.e("FirstFragment", "❌ Erreur recommandations IA: ${error.message}")
                    // Afficher un état vide en cas d'erreur
                    if (_binding != null) {
                        setupRecyclerView(
                            binding.root.findViewById(R.id.section_recommendations),
                            emptyList(),
                            "🤖 Recommandé pour vous"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("FirstFragment", "💥 Exception chargerRecommendations: ${e.message}", e)
                if (_binding != null) {
                    setupRecyclerView(
                        binding.root.findViewById(R.id.section_recommendations),
                        emptyList(),
                        "🤖 Recommandé pour vous"
                    )
                }
            } finally {
                isLoadingRecommendations = false
            }
        }
    }
    // ==========================================
    // ENRICHIR UNE SEULE RECOMMANDATION
    // ==========================================
    private suspend fun enrichRecommendation(index: Int, rec: com.mylibrary.model.BookRecommendation): Book? {
        return try {
            Log.d("FirstFragment", "📖 Traitement recommandation $index: ${rec.title}")

            var book = Book(
                id = -(index + 1),
                titre = rec.title,
                auteur = rec.author,
                isbn = rec.isbn,
                imageUrl = null,
                status = "à lire",
                note = null,
                memo = rec.reason,
                categories = null,
                publisher = null,
                publishedDate = null,
                pageCount = null,
                language = null,
                averageRatingGoogle = null,
                dateLecture = null,
                dateAjout = null,
                dateModification = null
            )

            // Chercher dans Google Books
            val searchQuery = if (!rec.isbn.isNullOrEmpty()) {
                "isbn:${rec.isbn}"
            } else {
                "${rec.title} ${rec.author}"
            }

            val googleResult = bookRepository.searchGoogleBooks(searchQuery)

            googleResult.onSuccess { searchResults ->
                searchResults.firstOrNull()?.let { searchResult ->
                    Log.d("FirstFragment", "✅ [$index] Trouvé: ${searchResult.title}")

                    book = book.copy(
                        imageUrl = searchResult.imageUrl,
                        isbn = searchResult.isbn ?: rec.isbn,
                        categories = searchResult.categories,
                        publisher = searchResult.publisher,
                        publishedDate = searchResult.publishedDate,
                        pageCount = searchResult.pageCount,
                        language = searchResult.language,
                        averageRatingGoogle = searchResult.averageRating
                    )
                }
            }

            book
        } catch (e: Exception) {
            Log.e("FirstFragment", "💥 Erreur enrichissement [$index]: ${e.message}")
            null // Retourner null en cas d'erreur
        }
    }

    private fun afficherLivres(livres: List<Book>) {
        if (_binding == null) return

        val enCours = livres.filter { it.status == "en cours" }
        val aLire = livres.filter { it.status == "à lire" }

        setupRecyclerView(binding.root.findViewById(R.id.section_en_cours), enCours, "En cours de lecture")
        setupRecyclerView(binding.root.findViewById(R.id.section_a_lire), aLire, "À lire")
    }

    private fun setupRecyclerView(sectionView: View, books: List<Book>, title: String) {
        if (_binding == null) return

        val titleView = sectionView.findViewById<TextView>(R.id.section_title)
        val recyclerView = sectionView.findViewById<RecyclerView>(R.id.recycler_books)
        val emptyState = sectionView.findViewById<View>(R.id.empty_state)
        val emptyMessage = sectionView.findViewById<TextView>(R.id.empty_message)

        titleView.text = title

        if (books.isEmpty()) {
            // Afficher l'état vide
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            // Messages personnalisés selon la section
            emptyMessage.text = when {
                title.contains("En cours") -> "Aucune lecture en cours.\nCommencez un nouveau livre !"
                title.contains("À lire") -> "Votre liste de lecture est vide.\nAjoutez des livres que vous souhaitez lire !"
                title.contains("Recommandé") || title.contains("🤖") -> "Ajoutez quelques livres à votre bibliothèque\npour recevoir des recommandations personnalisées !"
                else -> "Aucun livre pour le moment"
            }
        } else {
            // Afficher les livres
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE

            recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

            recyclerView.adapter = BooksAdapter(books) { bookId ->
                if (bookId < 0) {
                    val clickedBook = books.find { it.id == bookId }
                    if (clickedBook != null) {
                        Log.d("FirstFragment", "Navigation vers recommandation: ${clickedBook.titre}")
                        val action = FirstFragmentDirections.actionFirstFragmentToRecommendationDetailFragment(
                            title = clickedBook.titre,
                            author = clickedBook.auteur,
                            isbn = clickedBook.isbn,
                            reason = clickedBook.memo ?: ""
                        )
                        findNavController().navigate(action)
                    } else {
                        Log.e("FirstFragment", "Livre recommandé non trouvé avec ID: $bookId")
                    }
                } else {
                    Log.d("FirstFragment", "Navigation vers livre ID: $bookId")
                    val action = FirstFragmentDirections.actionFirstFragmentToBookDetailFragment(bookId)
                    findNavController().navigate(action)
                }
            }
        }
    }

    // ==========================================
    // FONCTION PUBLIQUE pour rafraîchir les recommandations
    // ==========================================
    fun refreshRecommendations() {
        cachedRecommendations = null
        chargerRecommendations()
    }
}