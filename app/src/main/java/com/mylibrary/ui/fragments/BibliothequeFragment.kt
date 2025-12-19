package com.mylibrary.ui.fragments

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mylibrary.ui.adapters.BooksGridAdapter
import com.mylibrary.R
import com.mylibrary.databinding.FragmentBibliothequeBinding
import com.mylibrary.model.Book
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager
import kotlinx.coroutines.launch
import android.util.Log

class BibliothequeFragment : Fragment() {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository

    private var _binding: FragmentBibliothequeBinding? = null
    private val binding get() = _binding!!

    private var allBooks = listOf<Book>()

    // Adaptateur personnalisé pour afficher un label quand la première option est sélectionnée
    inner class FilterSpinnerAdapter(
        context: android.content.Context,
        private val items: Array<String>,
        private val label: String
    ) : ArrayAdapter<String>(context, R.layout.spinner_item_custom, items) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.spinner_item_custom, parent, false)

            val textView = view as? android.widget.TextView
                ?: view.findViewById<android.widget.TextView>(android.R.id.text1)

            // Si c'est la première option (Tous ou Date récent), afficher le label
            if (position == 0) {
                textView?.text = label
                textView?.setTextColor(resources.getColor(R.color.clair_text_secondary, null))
            } else {
                textView?.text = items[position]
                textView?.setTextColor(resources.getColor(R.color.clair_text_primary, null))
            }

            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.spinner_item_custom, parent, false)

            val textView = view as? android.widget.TextView
                ?: view.findViewById<android.widget.TextView>(android.R.id.text1)

            textView?.text = items[position]
            textView?.setTextColor(resources.getColor(R.color.clair_text_primary, null))

            return view
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBibliothequeBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configuration grille
        val gridLayoutManager = GridLayoutManager(context, 2)
        binding.recyclerGrid.layoutManager = gridLayoutManager
        binding.recyclerGrid.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.bottom = 16
            }
        })

        // Configuration spinners
        setupSpinners()

        // Icône recherche
        binding.searchIcon.setOnClickListener {
            if (binding.searchBar.visibility == View.GONE) {
                binding.searchBar.visibility = View.VISIBLE
            } else {
                binding.searchBar.visibility = View.GONE
                binding.searchBar.text.clear()
            }
        }

        // Recherche en temps réel
        binding.searchBar.addTextChangedListener {
            applyFilters()
        }

        // --- NAVIGATION FOOTER ---
        val navBar = binding.navigationBar

        navBar.btnHome.setOnClickListener {
            findNavController().navigate(R.id.action_BibliothequeFragment_to_FirstFragment)
        }

        navBar.btnAdd.setOnClickListener {
            findNavController().navigate(R.id.action_BibliothequeFragment_to_SecondFragment)
        }

        navBar.btnAccount.setOnClickListener {
            findNavController().navigate(R.id.action_BibliothequeFragment_to_AccountFragment)
        }

        navBar.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_BibliothequeFragment_to_SearchFragment)
        }

        navBar.btnLibrary.setOnClickListener {}

        // Charger les livres
        chargerLivres()
    }

    override fun onResume() {
        super.onResume()
        chargerLivres()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupSpinners() {
        // Filtre Statut avec label personnalisé
        val statusList = arrayOf("Tous", "Lu", "En cours", "À lire", "Abandonné")
        val statusAdapter = FilterSpinnerAdapter(requireContext(), statusList, "Statut")
        statusAdapter.setDropDownViewResource(R.layout.spinner_item_custom)
        binding.spinnerFilterStatus.adapter = statusAdapter
        binding.spinnerFilterStatus.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
                // Force la mise à jour de l'affichage
                statusAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Filtre Catégorie avec label personnalisé
        val categoryAdapter = FilterSpinnerAdapter(requireContext(), arrayOf("Tous"), "Catégorie")
        categoryAdapter.setDropDownViewResource(R.layout.spinner_item_custom)
        binding.spinnerFilterGenre.adapter = categoryAdapter
        binding.spinnerFilterGenre.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
                // Force la mise à jour de l'affichage
                categoryAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Filtre Tri avec label personnalisé
        val sortList = arrayOf("Date (récent)", "Note (haute)", "Titre (A-Z)")
        val sortAdapter = FilterSpinnerAdapter(requireContext(), sortList, "Tri")
        sortAdapter.setDropDownViewResource(R.layout.spinner_item_custom)
        binding.spinnerSort.adapter = sortAdapter
        binding.spinnerSort.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
                // Force la mise à jour de l'affichage
                sortAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun applyFilters() {
        Log.d("BibliothequeFragment", "=== APPLY FILTERS ===")
        var filtered = allBooks

        // Recherche texte
        val query = binding.searchBar.text.toString().lowercase()
        if (query.isNotEmpty()) {
            filtered = filtered.filter { book ->
                book.titre.lowercase().contains(query) ||
                        book.auteur.lowercase().contains(query)
            }
        }

        // Filtre statut
        val selectedStatus = binding.spinnerFilterStatus.selectedItem.toString()
        if (selectedStatus != "Tous") {
            val statusDB = when(selectedStatus) {
                "Lu" -> "lu"
                "En cours" -> "en cours"
                "À lire" -> "à lire"
                "Abandonné" -> "abandonné"
                else -> ""
            }
            filtered = filtered.filter { it.status == statusDB }
        }

        // Filtre catégorie (vérifie si la catégorie est dans la liste)
        val selectedCategory = binding.spinnerFilterGenre.selectedItem?.toString()
        Log.d("BibliothequeFragment", "Catégorie sélectionnée: '$selectedCategory'")
        if (selectedCategory != null && selectedCategory != "Tous") {
            filtered = filtered.filter { book ->
                book.categories?.any { it.equals(selectedCategory, ignoreCase = true) } == true
            }
        }

        // Tri
        filtered = when(binding.spinnerSort.selectedItem.toString()) {
            "Date (récent)" -> filtered.sortedByDescending { it.dateAjout }
            "Note (haute)" -> filtered.sortedByDescending { it.note ?: 0 }
            "Titre (A-Z)" -> filtered.sortedBy { it.titre }
            else -> filtered
        }

        binding.recyclerGrid.adapter = BooksGridAdapter(filtered) { bookId ->
            val action = BibliothequeFragmentDirections.actionBibliothequeFragmentToBookDetailFragment(bookId)
            findNavController().navigate(action)
        }
    }

    private fun updateCategorySpinner() {
        Log.d("BibliothequeFragment", "=== UPDATE CATEGORY SPINNER ===")
        Log.d("BibliothequeFragment", "Nombre total de livres: ${allBooks.size}")

        // Log des catégories de chaque livre
        allBooks.forEach { book ->
            Log.d("BibliothequeFragment", "Livre: ${book.titre} | Categories: ${book.categories}")
        }

        // Extraire toutes les catégories de tous les livres
        val categories = allBooks
            .flatMap { it.categories ?: emptyList() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        Log.d("BiblioFragment","🔍 Liste finale du Spinner: $categories")
        Log.d("BiblioFragment", "🔍 Nombre de livres: ${allBooks.size}")

        val categoryList = mutableListOf("Tous")
        categoryList.addAll(categories)

        Log.d("BiblioFragment", "🔍 Liste finale du Spinner: $categoryList")

        // Créer un nouvel adaptateur avec les catégories mises à jour
        val categoryAdapter = FilterSpinnerAdapter(
            requireContext(),
            categoryList.toTypedArray(),
            "Catégorie"
        )
        categoryAdapter.setDropDownViewResource(R.layout.spinner_item_custom)
        binding.spinnerFilterGenre.adapter = categoryAdapter

        // Réattacher le listener
        binding.spinnerFilterGenre.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                applyFilters()
                categoryAdapter.notifyDataSetChanged()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun chargerLivres() {
        if (!connectionManager.isLoggedIn()) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        lifecycleScope.launch {
            val result = bookRepository.getAllMyBooks()

            result.onSuccess { books ->
                allBooks = books
                updateEmptyState()
                updateCategorySpinner()
                applyFilters()

                Log.d("BiblioFragment", "📚 Bibliothèque: ${books.size} livres chargés")

                // DEBUG : Affichage des livres chargés
                println("📚 Bibliothèque:")
                books.forEach { book ->
                    println("  - ${book.titre}")
                    println("    Category: ${book.categories}")
                    println("    Publisher: ${book.publisher}")
                    println("    Pages: ${book.pageCount}")
                }
            }

            result.onFailure { error ->
                Log.e("BiblioFragment", "❌ Erreur chargement: ${error.message}")

                // Si erreur 401/403, déconnexion
                if (error.message?.contains("401") == true || error.message?.contains("403") == true) {
                    connectionManager.clearToken()
                    findNavController().navigate(R.id.loginFragment)
                }
            }
        }
    }

    private fun updateEmptyState() {
        if (allBooks.isEmpty()) {
            binding.emptyStateLayout.visibility = View.VISIBLE
            binding.recyclerGrid.visibility = View.GONE
            binding.filtersLayout.visibility = View.GONE
        } else {
            binding.emptyStateLayout.visibility = View.GONE
            binding.recyclerGrid.visibility = View.VISIBLE
            binding.filtersLayout.visibility = View.VISIBLE
        }
    }
}