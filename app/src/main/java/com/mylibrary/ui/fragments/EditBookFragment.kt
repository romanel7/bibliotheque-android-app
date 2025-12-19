package com.mylibrary.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import coil.load
import com.mylibrary.R
import com.mylibrary.databinding.FragmentEditBookBinding
import com.mylibrary.network.BookRepository
import kotlinx.coroutines.launch
import android.util.Log

class EditBookFragment : Fragment() {

    private var _binding: FragmentEditBookBinding? = null
    private val binding get() = _binding!!

    private val args: EditBookFragmentArgs by navArgs()
    private lateinit var bookRepository: BookRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditBookBinding.inflate(inflater, container, false)
        bookRepository = BookRepository.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        loadBookData()

        // Bouton fermer
        binding.buttonClose.setOnClickListener {
            findNavController().navigateUp()
        }

        // Bouton enregistrer
        binding.buttonSave.setOnClickListener {
            saveChanges()
        }
    }

    private fun setupSpinner() {
        val statusList = arrayOf("À lire", "En cours", "Lu", "Abandonné")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, statusList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerStatut.adapter = adapter
    }

    private fun loadBookData() {
        lifecycleScope.launch {
            val result = bookRepository.getBookById(args.bookId)

            result.onSuccess { book ->
                Log.d("EditBook", "Livre chargé: ${book.titre}")

                // Image
                binding.photoLivre.load(book.imageUrl?.replace("http:", "https:")) {
                    crossfade(true)
                    placeholder(R.drawable.photo_placeholder)
                    error(R.drawable.photo_placeholder)
                }

                // Titre et auteur (non modifiables)
                binding.textTitre.text = book.titre
                binding.textAuteur.text = book.auteur

                // Statut
                val statusPosition = when(book.status) {
                    "à lire" -> 0
                    "en cours" -> 1
                    "lu" -> 2
                    "abandonné" -> 3
                    else -> 0
                }
                binding.spinnerStatut.setSelection(statusPosition)

                // Note
                book.note?.let {
                    binding.ratingBar.rating = it.toFloat()
                }

                // Mémo
                binding.editMemo.setText(book.memo ?: "")
            }

            result.onFailure { error ->
                Toast.makeText(requireContext(), "Erreur: ${error.message}", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }
        }
    }

    private fun saveChanges() {
        val status = when(binding.spinnerStatut.selectedItem.toString()) {
            "À lire" -> "à lire"
            "En cours" -> "en cours"
            "Lu" -> "lu"
            "Abandonné" -> "abandonné"
            else -> "à lire"
        }

        val note = binding.ratingBar.rating.toInt()
        val memo = binding.editMemo.text.toString()

        lifecycleScope.launch {
            val result = bookRepository.updateBook(
                bookId = args.bookId,
                status = status,
                note = note,
                memo = memo
            )

            result.onSuccess {
                Toast.makeText(requireContext(), "Livre modifié avec succès", Toast.LENGTH_SHORT).show()
                findNavController().navigateUp()
            }

            result.onFailure { error ->
                Toast.makeText(
                    requireContext(), "Erreur: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}