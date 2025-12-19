package com.mylibrary.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.mylibrary.R
import com.mylibrary.databinding.FragmentRecommendationDetailBinding
import com.mylibrary.network.ConnectionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import com.mylibrary.network.BookRepository


class RecommendationDetailFragment : Fragment() {

    private val args: RecommendationDetailFragmentArgs by navArgs()

    private var _binding: FragmentRecommendationDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRecommendationDetailBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bouton fermer
        binding.buttonClose.setOnClickListener {
            findNavController().navigateUp()
        }

        // Bouton Ajouter - Ajoute directement à la bibliothèque
        binding.btnAdd.setOnClickListener {
            addBookToLibrary()
        }

        // Charger les détails
        loadRecommendationDetails()
    }

    private fun loadRecommendationDetails() {
        Log.d("RecommendationDetail", "  Chargement détails recommandation")
        Log.d("RecommendationDetail", "   - Titre: ${args.title}")
        Log.d("RecommendationDetail", "   - Auteur: ${args.author}")
        Log.d("RecommendationDetail", "   - ISBN: ${args.isbn}")

        // Afficher les infos de base immédiatement
        binding.textBookTitle.text = args.title
        binding.textBookAuthor.text = "par ${args.author}"
        binding.textReason.text = args.reason

        // Construire la requête de recherche
        val searchQuery = if (!args.isbn.isNullOrEmpty()) {
            Log.d("RecommendationDetail", " Recherche par ISBN: ${args.isbn}")
            "isbn:${args.isbn}"
        } else {
            val query = "${args.title} ${args.author}"
            Log.d("RecommendationDetail", " Recherche par titre+auteur: $query")
            query
        }

        // Charger les infos depuis Google Books
        lifecycleScope.launch {
            try {
                val result = bookRepository.searchGoogleBooks(searchQuery)

                result.onSuccess { searchResults ->
                    val searchResult = searchResults.firstOrNull()

                    if (searchResult != null) {
                        Log.d("RecommendationDetail", "Google Books trouvé!")
                        Log.d("RecommendationDetail", "   - Titre: ${searchResult.title}")
                        Log.d("RecommendationDetail", "   - Image: ${searchResult.imageUrl}")
                        Log.d("RecommendationDetail", "   - Publisher: ${searchResult.publisher}")
                        Log.d("RecommendationDetail", "   - Pages: ${searchResult.pageCount}")
                        Log.d("RecommendationDetail", "   - ISBN: ${searchResult.isbn}")

                        // Charger l'image de couverture
                        searchResult.imageUrl?.let { imageUrl ->
                            Log.d("RecommendationDetail", "🖼️ Chargement image: $imageUrl")
                            binding.imageBookCover.load(imageUrl) {
                                crossfade(true)
                                placeholder(R.drawable.ic_launcher_foreground)
                                error(R.drawable.book_cover_white)
                            }
                        } ?: run {
                            Log.w("RecommendationDetail", "⚠️ Pas d'image")
                            binding.imageBookCover.setImageResource(R.drawable.ic_launcher_foreground)
                        }

                        // Afficher l'ISBN (celui trouvé ou celui reçu)
                        val isbn = searchResult.isbn ?: args.isbn
                        if (!isbn.isNullOrEmpty()) {
                            binding.layoutIsbn.visibility = View.VISIBLE
                            binding.textIsbn.text = isbn
                        }

                        // Afficher l'éditeur
                        searchResult.publisher?.let { publisher ->
                            Log.d("RecommendationDetail", "📖 Affichage éditeur: $publisher")
                            binding.layoutPublisher.visibility = View.VISIBLE
                            binding.textPublisher.text = publisher
                        }

                        // Afficher le nombre de pages
                        searchResult.pageCount?.let { pageCount ->
                            Log.d("RecommendationDetail", "📄 Affichage pages: $pageCount")
                            binding.layoutPageCount.visibility = View.VISIBLE
                            binding.textPageCount.text = "$pageCount pages"
                        }

                        // Afficher la langue
                        searchResult.language?.let { language ->
                            Log.d("RecommendationDetail", "🌍 Affichage langue: $language")
                            binding.layoutLanguage.visibility = View.VISIBLE
                            binding.textLanguage.text = when (language) {
                                "fr" -> "Français"
                                "en" -> "Anglais"
                                "es" -> "Espagnol"
                                "de" -> "Allemand"
                                else -> language.uppercase()
                            }
                        }
                    } else {
                        Log.w("RecommendationDetail", "⚠️ Aucun résultat Google Books")
                        binding.imageBookCover.setImageResource(R.drawable.ic_launcher_foreground)
                    }
                }

                result.onFailure { error ->
                    Log.e("RecommendationDetail", "❌ Échec Google Books: ${error.message}")
                    binding.imageBookCover.setImageResource(R.drawable.ic_launcher_foreground)
                }
            } catch (e: Exception) {
                Log.e("RecommendationDetail", "💥 Exception: ${e.message}", e)
                binding.imageBookCover.setImageResource(R.drawable.ic_launcher_foreground)
            }
        }
    }

    private fun addBookToLibrary() {
        if (!connectionManager.isLoggedIn()) {
            Toast.makeText(requireContext(), "Veuillez vous connecter", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.loginFragment)
            return
        }

        binding.btnAdd.isEnabled = false
        binding.btnAdd.text = "Ajout en cours..."

        lifecycleScope.launch {
            try {
                // Chercher les infos complètes dans Google Books
                val searchQuery = if (!args.isbn.isNullOrEmpty()) {
                    "isbn:${args.isbn}"
                } else {
                    "${args.title} ${args.author}"
                }

                val googleResult = bookRepository.searchGoogleBooks(searchQuery)
                val searchResult = googleResult.getOrNull()?.firstOrNull()

                // Préparer les données du livre (enrichies si possible)
                val jsonObject = JSONObject().apply {
                    put("titre", args.title)
                    put("auteur", args.author)
                    put("status", "à lire")
                    put("note", 0)
                    put("memo", "Recommandé par l'IA : ${args.reason}")

                    // Utiliser les données Google Books si disponibles
                    if (searchResult != null) {
                        searchResult.imageUrl?.let { put("image_url", it) }
                        searchResult.isbn?.let { put("isbn", it) }
                        searchResult.publisher?.let { put("publisher", it) }
                        searchResult.publishedDate?.let { put("published_date", it) }
                        searchResult.pageCount?.let { put("page_count", it) }
                        searchResult.language?.let { put("language", it) }
                        searchResult.averageRating?.let { put("average_rating_google", it) }

                        searchResult.categories?.let { cats ->
                            val categoriesArray = JSONArray()
                            cats.forEach { categoriesArray.put(it) }
                            put("categories", categoriesArray)
                        }

                        Log.d("RecommendationDetail", "✅ Livre enrichi avec Google Books")
                    } else if (!args.isbn.isNullOrEmpty()) {
                        // Fallback sur l'ISBN fourni
                        put("isbn", args.isbn)
                    }
                }

                Log.d("RecommendationDetail", "Ajout du livre: ${jsonObject.toString(2)}")

                val response = withContext(Dispatchers.IO) {
                    connectionManager.post("/livres", jsonObject.toString(), requiresAuth = true)
                }

                when {
                    response.isSuccessful -> {
                        Toast.makeText(requireContext(), "✅ Livre ajouté à \"À lire\" !", Toast.LENGTH_SHORT).show()
                        findNavController().navigateUp()
                    }
                    response.code == 401 || response.code == 403 -> {
                        Toast.makeText(requireContext(), "Session expirée. Veuillez vous reconnecter.", Toast.LENGTH_LONG).show()
                        connectionManager.clearToken()
                        findNavController().navigate(R.id.loginFragment)
                    }
                    else -> {
                        Toast.makeText(requireContext(), "Erreur (${response.code}): ${response.body}", Toast.LENGTH_LONG).show()
                        binding.btnAdd.isEnabled = true
                        binding.btnAdd.text = "➕ Ajouter à ma bibliothèque"
                    }
                }
            } catch (e: Exception) {
                Log.e("RecommendationDetail", "Erreur ajout livre", e)
                Toast.makeText(requireContext(), "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
                binding.btnAdd.isEnabled = true
                binding.btnAdd.text = "➕ Ajouter à ma bibliothèque"
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}