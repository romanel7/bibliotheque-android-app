package com.mylibrary.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.mylibrary.databinding.FragmentSecondBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import androidx.recyclerview.widget.LinearLayoutManager
import com.mylibrary.R
import com.mylibrary.ui.adapters.SearchAdapter
import com.mylibrary.model.SearchResult
import com.mylibrary.network.ConnectionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import org.json.JSONArray


class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null
    private val binding get() = _binding!!
    private val args: SecondFragmentArgs by navArgs()
    private lateinit var connectionManager: ConnectionManager

    private var selectedBookTitle: String = ""
    private var selectedBookAuthor: String = ""
    private var selectedBookImageUrl: String = ""
    private var selectedBookCategories: List<String>? = null
    private var selectedBookIsbn: String? = null
    private var selectedBookPublisher: String? = null
    private var selectedBookPublishedDate: String? = null
    private var selectedBookPageCount: Int? = null
    private var selectedBookLanguage: String? = null

    private lateinit var searchAdapter: SearchAdapter
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("SecondFragment", "=== onCreateView APPELÉ ===")
        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        Log.d("SecondFragment", "Binding et ConnectionManager initialisés")
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchListener()

        // ✅ PRÉ-REMPLISSAGE ACTIVÉ
        prefillFormFromArgs()

        // Bouton pour ajouter le livre
        binding.buttonAjouter.setOnClickListener {
            saveBook()
        }

        // Bouton pour fermer la fenêtre (le X en haut)
        binding.buttonClose.setOnClickListener {
            findNavController().navigateUp()
        }

        // 📸 NOUVEAU : Bouton scanner code-barres
        binding.buttonScanBarcode.setOnClickListener {
            startBarcodeScanner()
        }
    }

    // 📸 NOUVEAU : Lancer le scanner de code-barres
    private fun startBarcodeScanner() {
        val integrator = IntentIntegrator.forSupportFragment(this)
        integrator.setDesiredBarcodeFormats(
            IntentIntegrator.EAN_13,  // Code-barres classique des livres
            IntentIntegrator.EAN_8,
            IntentIntegrator.CODE_39,
            IntentIntegrator.CODE_128
        )
        integrator.setPrompt("Scannez le code-barres ISBN du livre")
        integrator.setCameraId(0) // Caméra arrière
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(false)
        integrator.setOrientationLocked(true)
        integrator.initiateScan()
    }

    // 📥 NOUVEAU : Récupérer le résultat du scan
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result: IntentResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)

        if (result.contents != null) {
            val scannedCode = result.contents
            Log.d("SecondFragment", "Code-barres scanné : $scannedCode")
            Toast.makeText(context, "ISBN scanné : $scannedCode", Toast.LENGTH_SHORT).show()

            // Rechercher le livre avec l'ISBN scanné
            searchBookByISBN(scannedCode)
        } else {
            Toast.makeText(context, "Scan annulé", Toast.LENGTH_SHORT).show()
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    // 🔍 NOUVEAU : Rechercher un livre par ISBN
    private fun searchBookByISBN(isbn: String) {
        binding.progressBarSearch.visibility = View.VISIBLE
        binding.recyclerSearchResults.visibility = View.GONE
        binding.emptyStateLogo.visibility = View.GONE

        val client = OkHttpClient()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://www.googleapis.com/books/v1/volumes?q=isbn:$isbn"

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()

                if (response.isSuccessful && jsonResponse != null) {
                    val books = parseBookResults(jsonResponse)

                    withContext(Dispatchers.Main) {
                        binding.progressBarSearch.visibility = View.GONE

                        if (books.isNotEmpty()) {
                            // Sélectionner directement le premier livre trouvé
                            val book = books.first()
                            onBookSelected(book)
                        } else {
                            binding.emptyStateLogo.visibility = View.VISIBLE
                            Toast.makeText(
                                context,
                                "Aucun livre trouvé pour l'ISBN : $isbn",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBarSearch.visibility = View.GONE
                        binding.emptyStateLogo.visibility = View.VISIBLE
                        Toast.makeText(
                            context,
                            "Erreur de recherche (${response.code})",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.emptyStateLogo.visibility = View.VISIBLE
                    Toast.makeText(
                        context,
                        "Erreur : ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    e.printStackTrace()
                }
            }
        }
    }

    // Pré-remplissage depuis les arguments
    private fun prefillFormFromArgs() {
        // Si on a des données (venant du SearchFragment)
        if (args.bookTitle != null && args.bookAuthors != null) {
            // Remplir les variables sélectionnées
            selectedBookTitle = args.bookTitle ?: ""
            selectedBookAuthor = args.bookAuthors ?: ""
            selectedBookImageUrl = args.bookImageUrl ?: ""

            // Récupérer les catégories
            selectedBookCategories = args.bookCategories?.split(",")?.filter { it.isNotBlank() }
            Log.d("SecondFragment", "Catégories depuis args: $selectedBookCategories")

            selectedBookIsbn = args.bookIsbn

            // Afficher dans le formulaire
            binding.textTitre.text = selectedBookTitle
            binding.textAuteur.text = selectedBookAuthor

            // Charger l'image
            if (selectedBookImageUrl.isNotEmpty()) {
                binding.photoLivre.load(selectedBookImageUrl) {
                    crossfade(true)
                    error(R.drawable.book_cover_white)
                    placeholder(R.drawable.photo_placeholder)
                }
            }

            // Masquer les éléments de recherche et afficher le formulaire
            binding.recyclerSearchResults.visibility = View.GONE
            binding.emptyStateLogo.visibility = View.GONE
            binding.scrollBookDetails.visibility = View.VISIBLE
            binding.buttonAjouter.visibility = View.VISIBLE

            // Message de confirmation
            Toast.makeText(
                context,
                "Livre pré-rempli depuis la recherche!",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            // Mode recherche normale (bouton + de la nav bar)
            binding.recyclerSearchResults.visibility = View.GONE
            binding.scrollBookDetails.visibility = View.GONE
            binding.buttonAjouter.visibility = View.GONE
            binding.emptyStateLogo.visibility = View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(emptyList()) { book ->
            onBookSelected(book)
        }
        binding.recyclerSearchResults.layoutManager = LinearLayoutManager(context)
        binding.recyclerSearchResults.adapter = searchAdapter
    }

    private fun setupSearchListener() {
        binding.editSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()

                // Annule la recherche précédente
                searchJob?.cancel()

                if (query.length >= 2) {
                    // ✅ Cache le logo quand on commence à chercher
                    binding.emptyStateLogo.visibility = View.GONE

                    // Attend 500ms avant de lancer la recherche (debounce)
                    searchJob = CoroutineScope(Dispatchers.Main).launch {
                        delay(500)
                        searchBooks(query)
                    }
                } else {
                    // ✅ Affiche le logo si moins de 3 caractères
                    binding.emptyStateLogo.visibility = View.VISIBLE

                    // Cache les résultats si moins de 3 caractères
                    binding.recyclerSearchResults.visibility = View.GONE
                    binding.scrollBookDetails.visibility = View.GONE
                    binding.buttonAjouter.visibility = View.GONE
                }
            }
        })
    }

    private fun searchBooks(query: String) {
        binding.progressBarSearch.visibility = View.VISIBLE
        binding.recyclerSearchResults.visibility = View.GONE
        binding.emptyStateLogo.visibility = View.GONE

        val client = OkHttpClient()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://www.googleapis.com/books/v1/volumes?q=$query&maxResults=40"

                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val jsonResponse = response.body?.string()

                if (response.isSuccessful && jsonResponse != null) {
                    val books = parseBookResults(jsonResponse)

                    withContext(Dispatchers.Main) {
                        binding.progressBarSearch.visibility = View.GONE

                        if (books.isNotEmpty()) {
                            searchAdapter.updateData(books)
                            binding.recyclerSearchResults.visibility = View.VISIBLE
                            binding.emptyStateLogo.visibility = View.GONE
                        } else {
                            binding.emptyStateLogo.visibility = View.VISIBLE
                            Toast.makeText(context, "Aucun résultat trouvé", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        binding.progressBarSearch.visibility = View.GONE
                        binding.emptyStateLogo.visibility = View.VISIBLE
                        Toast.makeText(context, "Erreur de recherche", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarSearch.visibility = View.GONE
                    binding.emptyStateLogo.visibility = View.VISIBLE
                    Toast.makeText(context, "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseBookResults(jsonString: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val jsonObject = JSONObject(jsonString)
        val items = jsonObject.optJSONArray("items") ?: return results

        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val volumeInfo = item.getJSONObject("volumeInfo")

            val title = volumeInfo.optString("title", "Titre inconnu")

            val authorsArray = volumeInfo.optJSONArray("authors")
            val authors = if (authorsArray != null && authorsArray.length() > 0) {
                (0 until authorsArray.length()).joinToString(", ") {
                    authorsArray.getString(it)
                }
            } else {
                "Auteur inconnu"
            }

            val industryIdentifiers = volumeInfo.optJSONArray("industryIdentifiers")
            var isbn: String? = null
            if (industryIdentifiers != null) {
                for (j in 0 until industryIdentifiers.length()) {
                    val identifier = industryIdentifiers.getJSONObject(j)
                    val type = identifier.optString("type")
                    if (type == "ISBN_13" || type == "ISBN_10") {
                        isbn = identifier.optString("identifier")
                        break
                    }
                }
            }

            val imageLinks = volumeInfo.optJSONObject("imageLinks")
            var imageUrl: String? = null

            if (imageLinks != null) {
                imageUrl = imageLinks.optString("thumbnail", null)
                    ?: imageLinks.optString("smallThumbnail", null)
                            ?: imageLinks.optString("small", null)
                            ?: imageLinks.optString("medium", null)
                            ?: imageLinks.optString("large", null)

                if (imageUrl != null) {
                    imageUrl = imageUrl.replace("http:", "https:")
                }
            }

            val description = volumeInfo.optString("description", "")

            // Catégories
            val categoriesArray = volumeInfo.optJSONArray("categories")
            val categories = if (categoriesArray != null && categoriesArray.length() > 0) {
                (0 until categoriesArray.length()).map { idx ->
                    categoriesArray.getString(idx).split("/").firstOrNull()?.trim() ?: ""
                }.filter { it.isNotBlank() }.distinct()
            } else null

            // ✅ NOUVEAU : Publisher (Éditeur)
            val publisher = volumeInfo.optString("publisher", null)
                ?.takeIf { it.isNotBlank() }

            // ✅ NOUVEAU : Published Date (Date de publication)
            val publishedDate = volumeInfo.optString("publishedDate", null)
                ?.takeIf { it.isNotBlank() }

            // ✅ NOUVEAU : Page Count (Nombre de pages)
            val pageCount = volumeInfo.optInt("pageCount", 0)
                .takeIf { it > 0 }

            // ✅ NOUVEAU : Language (Langue)
            val language = volumeInfo.optString("language", "fr")
                ?.takeIf { it.isNotBlank() }

            Log.d("SecondFragment", "Parsing: $title")
            Log.d("SecondFragment", "  - Image: $imageUrl")
            Log.d("SecondFragment", "  - Publisher: $publisher")
            Log.d("SecondFragment", "  - Pages: $pageCount")
            Log.d("SecondFragment", "  - Published: $publishedDate")

            // ✅ IMPORTANT : Ajoute TOUS les paramètres !
            results.add(SearchResult(
                title = title,
                authors = authors,
                isbn = isbn,
                imageUrl = imageUrl,
                description = description,
                categories = categories,
                publisher = publisher,        // ✅ NOUVEAU
                publishedDate = publishedDate, // ✅ NOUVEAU
                pageCount = pageCount,         // ✅ NOUVEAU
                language = language            // ✅ NOUVEAU
            ))
        }

        return results
    }


    private fun onBookSelected(book: SearchResult) {
        selectedBookTitle = book.title
        selectedBookAuthor = book.authors
        selectedBookImageUrl = book.imageUrl ?: ""
        selectedBookCategories = book.categories
        selectedBookIsbn = book.isbn
        selectedBookPublisher = book.publisher
        selectedBookPublishedDate = book.publishedDate
        selectedBookPageCount = book.pageCount
        selectedBookLanguage = book.language

        Log.d("SecondFragment", "Book selected - Image URL: ${book.imageUrl}")
        Log.d("SecondFragment", "Book selected - Categories: ${book.categories}")
        Log.d("SecondFragment", "Book selected - ISBN: ${book.isbn}")
        Log.d("SecondFragment", "Book selected - Publisher: ${book.publisher}")

        binding.textTitre.text = selectedBookTitle
        binding.textAuteur.text = selectedBookAuthor

        binding.photoLivre.load(selectedBookImageUrl) {
            crossfade(true)
            error(R.drawable.book_cover_white)
            placeholder(R.drawable.photo_placeholder)
        }

        binding.recyclerSearchResults.visibility = View.GONE
        binding.emptyStateLogo.visibility = View.GONE
        binding.scrollBookDetails.visibility = View.VISIBLE
        binding.buttonAjouter.visibility = View.VISIBLE

        Toast.makeText(context, "Livre sélectionné: ${book.title}", Toast.LENGTH_SHORT).show()
    }

    private fun saveBook() {
        val titre = selectedBookTitle
        val auteur = selectedBookAuthor

        if (titre.isBlank() || auteur.isBlank()) {
            Toast.makeText(context, "Veuillez d'abord rechercher et sélectionner un livre.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!connectionManager.isLoggedIn()) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        val status = binding.spinnerStatut.selectedItem.toString().lowercase()
        val note = binding.ratingBar.rating.toInt()
        val memo = binding.editMemo.text.toString()

        Log.d("SecondFragment", "Image URL: $selectedBookImageUrl")
        Log.d("SecondFragment", "=== DEBUG SAVE BOOK ===")
        Log.d("SecondFragment", "Statut sélectionné: '$status'")
        Log.d("SecondFragment", "Statut mappé: '$status'")

        Log.d("SecondFragment", "Catégories sélectionnées: $selectedBookCategories")

        val jsonObject = JSONObject().apply {
            put("titre", titre)
            put("auteur", auteur)
            put("status", status)
            put("note", note)
            put("memo", memo)
            put("image_url", selectedBookImageUrl)

            selectedBookIsbn?.let { put("isbn", it) }
            selectedBookPublisher?.let { put("publisher", it) }
            selectedBookPublishedDate?.let { put("published_date", it) }
            selectedBookPageCount?.let { put("page_count", it) }
            selectedBookLanguage?.let { put("language", it) }

            // Catégories
            if (selectedBookCategories != null && selectedBookCategories!!.isNotEmpty()) {
                val categoriesArray = JSONArray()
                selectedBookCategories!!.forEach { categoriesArray.put(it) }
                put("categories", categoriesArray)
            }
        }


        Log.d("SecondFragment", "JSON envoyé: ${jsonObject.toString(2)}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = connectionManager.post(
                    "/livres",
                    jsonObject.toString(),
                    requiresAuth = true
                )

                Log.d("SecondFragment", "Response code: ${response.code}")
                Log.d("SecondFragment", "Response body: ${response.body}")

                withContext(Dispatchers.Main) {
                    when {
                        response.isSuccessful -> {
                            Toast.makeText(context, "Livre ajouté avec succès!", Toast.LENGTH_SHORT).show()
                            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
                        }
                        response.code == 401 || response.code == 403 -> {
                            Toast.makeText(context, "Session expirée. Veuillez vous reconnecter.", Toast.LENGTH_LONG).show()
                            connectionManager.clearToken()
                            findNavController().navigate(R.id.loginFragment)
                        }
                        else -> {
                            Toast.makeText(context, "Erreur (${response.code}) : ${response.body}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erreur réseau : ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}