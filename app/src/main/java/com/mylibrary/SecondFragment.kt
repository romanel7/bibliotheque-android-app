package com.mylibrary

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.mylibrary.databinding.FragmentSecondBinding
import android.widget.ArrayAdapter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ajouter un livre
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonClose.setOnClickListener {
            findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
        }

        // Configuration du bouton Ajouter
        binding.buttonAjouter.setOnClickListener {
            ajouterLivre()
        }

        // Configuration du Spinner pour les statuts
        val statutsList = arrayOf("À lire", "En cours", "Lu")

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item_custom,   // style personnalisé dans : res > layout > spinner_item_custom.xml
            statutsList
        )
        adapter.setDropDownViewResource(R.layout.spinner_item_custom)
        binding.spinnerStatut.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Ajouter un livre à la bibliothèque
    private fun ajouterLivre() {
        // Récupération des valeurs
        val titre = binding.editTitre.text.toString().trim()
        val auteur = binding.editAuteur.text.toString().trim()
        val statut = binding.spinnerStatut.selectedItem.toString()
        val note = binding.ratingBar.rating.toInt()
        val memo = binding.editMemo.text.toString().trim()

        // Vérification
        if (titre.isEmpty() || auteur.isEmpty()) {
            println("Erreur: Titre ou auteur manquant")
            return
        }

        // Conversion du statut en format base de données
        val statutDB = when(statut) {
            "À lire" -> "non_lu"
            "En cours" -> "en_cours"
            "Lu" -> "lu"
            "Abandonné" -> "abandonne"
            else -> "non_lu"
        }

        // Création du JSON
        val json = """
        {
            "titre": "$titre",
            "auteur": "$auteur",
            "note": $note,
            "status": "$statutDB",
            "memo": "$memo"
        }
    """.trimIndent()

        println("JSON à envoyer: $json")

        // Envoi au serveur
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = OkHttpClient()
                val mediaType = "application/json".toMediaType()
                val body = json.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("http://10.0.2.2:3000/api/livres")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        println("✅ Livre ajouté avec succès!")
                        // Retour à la page d'accueil
                        findNavController().navigate(R.id.action_SecondFragment_to_FirstFragment)
                    } else {
                        println("❌ Erreur: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                println("❌ Exception: ${e.message}")
            }
        }
    }
}