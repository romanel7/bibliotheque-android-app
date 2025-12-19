package com.mylibrary.ui.fragments

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.SearchView
import com.mylibrary.databinding.FragmentSearchBinding
import kotlinx.coroutines.launch
import android.widget.LinearLayout
import android.widget.Toast
import com.mylibrary.R
import com.mylibrary.ui.adapters.SearchAdapter
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager

class SearchFragment : Fragment() {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository  // ← NOUVEAU

    
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchAdapter: SearchAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())  // ← NOUVEAU
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Navigation vers SecondFragment AVEC les données du livre
        searchAdapter = SearchAdapter(emptyList()) { searchResult ->
            val categoriesString = searchResult.categories?.joinToString(",")

            val action = SearchFragmentDirections.actionSearchFragmentToSecondFragment(
                bookTitle = searchResult.title,
                bookAuthors = searchResult.authors,
                bookIsbn = searchResult.isbn,
                bookDescription = searchResult.description,
                bookImageUrl = searchResult.imageUrl,
                bookCategories = categoriesString
            )
            findNavController().navigate(action)
        }

        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter
        }

        setupSearchView()
        setupFooterNavigation(view)
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrBlank()) {
                    searchBooks(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    // ==========================================
    // ✅ NOUVEAU : Utilise BookRepository
    // ==========================================
    private fun searchBooks(query: String) {
        binding.loadingSpinner.visibility = View.VISIBLE
        binding.resultsRecyclerView.visibility = View.GONE

        lifecycleScope.launch {
            val result = bookRepository.searchGoogleBooks(query)  // ← NOUVEAU : Plus simple !

            binding.loadingSpinner.visibility = View.GONE
            binding.resultsRecyclerView.visibility = View.VISIBLE

            result.onSuccess { results ->
                searchAdapter.updateData(results)
                if (results.isEmpty()) {
                    Toast.makeText(context, "Aucun résultat trouvé.", Toast.LENGTH_SHORT).show()
                }
            }

            result.onFailure { error ->
                Toast.makeText(
                    context,
                    "Erreur de recherche: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun setupFooterNavigation(rootView: View) {
        // Bouton Maison (Accueil)
        rootView.findViewById<LinearLayout>(R.id.btn_home)?.setOnClickListener {
            findNavController().navigate(R.id.action_SearchFragment_to_FirstFragment)
        }

        // Bouton Compte (Stats et Paramètres)
        rootView.findViewById<LinearLayout>(R.id.btn_account)?.setOnClickListener {
            findNavController().navigate(R.id.action_SearchFragment_to_AccountFragment)
        }

        // Bouton Ajouter (bouton +)
        rootView.findViewById<LinearLayout>(R.id.btn_add)?.setOnClickListener {
            // Navigation vers SecondFragment vide (sans données préremplies)
            val action = SearchFragmentDirections.actionSearchFragmentToSecondFragment(
                bookTitle = null,
                bookAuthors = null,
                bookIsbn = null,
                bookDescription = null,
                bookImageUrl = null
            )
            findNavController().navigate(action)
        }

        // Bouton Bibliothèque
        rootView.findViewById<LinearLayout>(R.id.btn_library)?.setOnClickListener {
            findNavController().navigate(R.id.action_SearchFragment_to_BibliothequeFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}