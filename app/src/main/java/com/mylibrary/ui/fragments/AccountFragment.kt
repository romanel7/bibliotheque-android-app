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
import com.mylibrary.R
import com.mylibrary.databinding.FragmentAccountBinding
import com.mylibrary.model.Book
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager
import kotlinx.coroutines.launch
import com.mylibrary.model.UserProfile
import kotlin.math.min
// Importation du ProfilePictureManager défini dans SettingsFragment
import com.mylibrary.ui.fragments.SettingsFragment.ProfilePictureManager
import com.mylibrary.ui.adapters.BadgesAdapter
import com.mylibrary.model.BadgesResponse

class AccountFragment : Fragment() {

    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository

    private var _binding: FragmentAccountBinding? = null
    private val binding get() = _binding!!
    private lateinit var badgesAdapter: BadgesAdapter

    // Déclaration des Photos de Profil pour le chargement
    private val profilePictures = mapOf(
        "pp_pandas" to R.drawable.pp_pandas,
        "pp_chian" to R.drawable.pp_chien,
        "pp_chat" to R.drawable.pp_chat,
        "pp_hiboux" to R.drawable.pp_hiboux,
        "pp_renne" to R.drawable.pp_renne,
        "Défaut" to R.drawable.ic_launcher_foreground
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountBinding.inflate(inflater, container, false)
        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bouton Paramètres
        binding.cardSettings.setOnClickListener {
            Log.d("AccountFragment", "Bouton paramètres cliqué!")
            findNavController().navigate(R.id.action_AccountFragment_to_SettingsFragment)
        }

        // Navigation bar
        binding.navigationBar.btnHome.setOnClickListener {
            findNavController().navigate(R.id.action_AccountFragment_to_FirstFragment)
        }
        binding.navigationBar.btnLibrary.setOnClickListener {
            findNavController().navigate(R.id.action_AccountFragment_to_BibliothequeFragment)
        }
        binding.navigationBar.btnSearch.setOnClickListener {
            findNavController().navigate(R.id.action_AccountFragment_to_SearchFragment)
        }
        binding.navigationBar.btnAdd.setOnClickListener {
            findNavController().navigate(R.id.action_AccountFragment_to_SecondFragment)
        }
        binding.navigationBar.btnAccount.setOnClickListener {
            // Déjà sur la page Account
        }

        // Charger la Photo de Profil au démarrage
        chargerProfilPicture()
        chargerProfil()
        chargerStats()
        chargerBadges()
    }

    override fun onResume() {
        super.onResume()
        // Mettre à jour la Photo de Profil lors du retour
        chargerProfilPicture()
        chargerProfil()
        chargerStats()
        chargerBadges()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * NOUVEAU : Charge la photo de profil sauvegardée dans les SharedPreferences.
     */
    private fun chargerProfilPicture() {
        // 1. Récupère le nom de la PP enregistrée ("pp_pandas", "Défaut", etc.)
        val ppName = ProfilePictureManager.getSavedProfilePictureName(requireContext())

        // 2. Trouve l'ID de ressource correspondant dans la map, utilise 'Défaut' si non trouvé
        val ppId = ProfilePictureManager.getProfilePictureId(profilePictures, ppName)

        // 3. Applique l'image à l'ImageView
        binding.profilePicture.setImageResource(ppId)
    }

    private fun chargerProfil() {
        if (!connectionManager.isLoggedIn()) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        lifecycleScope.launch {
            val result = bookRepository.getUserProfile()

            result.onSuccess { profile ->
                // Afficher le nom d'utilisateur
                binding.username.text = profile.username
                Log.d("AccountFragment", "Profil chargé: ${profile.username}")
            }

            result.onFailure { error ->
                Log.e("AccountFragment", "Erreur profil: ${error.message}")
                // En cas d'erreur, afficher un nom par défaut
                binding.username.text = "Utilisateur"
            }
        }
    }

    private fun chargerStats() {
        if (!connectionManager.isLoggedIn()) {
            findNavController().navigate(R.id.loginFragment)
            return
        }

        lifecycleScope.launch {
            val result = bookRepository.getAllMyBooks()

            result.onSuccess { livres ->
                calculerStats(livres)
            }

            result.onFailure { error ->
                Toast.makeText(context, "Erreur: ${error.message}", Toast.LENGTH_SHORT).show()

                // Si erreur 401/403, déconnexion
                if (error.message?.contains("401") == true || error.message?.contains("403") == true) {
                    connectionManager.clearToken()
                    findNavController().navigate(R.id.loginFragment)
                }
            }
        }
    }

    private fun calculerStats(livres: List<Book>) {
        val total = livres.size
        val lus = livres.count { it.status == "lu" }
        val aLire = livres.count { it.status == "à lire" }
        val livresLusList = livres.filter { it.status == "lu" }

        // Statistiques en-tête
        binding.booksAddCount.text = total.toString()
        binding.booksReadCount.text = lus.toString()
        binding.booksToReadCount.text = aLire.toString()

        // Auteur préféré (celui avec le plus de livres LUS)
        val auteurPrefere = livresLusList.groupBy { it.auteur }
            .maxByOrNull { it.value.size }
            ?.key
        binding.favoriteAuteur.text = auteurPrefere ?: "Personne pour l'instant :)"

        // Catégorie préférée (celle avec le plus de livres LUS)
        val categoriePrefere = livresLusList
            .flatMap { it.categories ?: emptyList() }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key
        binding.favoriteGenre.text = categoriePrefere ?: "Aucune pour l'instant"

        // ==========================================================
        // LOGIQUE DE PROGRESSION  - NOUVEAUX PALIERS
        // ==========================================================

        val livresLusCompteur = lus

        // Définition des paliers : (Seuil atteint, Seuil suivant, Nom)
        val levelTiers = listOf(
            Triple(0, 1, "Commencez votre aventure"),  // ✅ Niveau 0 avec message
            Triple(1, 3, "Débutant"),
            Triple(3, 5, "Novice"),
            Triple(5, 10, "Amateur"),
            Triple(10, 20, "Passionné"),
            Triple(20, 30, "Expert"),
            Triple(30, 40, "Maître"),
            Triple(40, 999, "Légende")
        )

// Trouver le niveau actuel
        val currentLevelTier = levelTiers.lastOrNull { livresLusCompteur >= it.first } ?: levelTiers.first()
        val (previousThreshold, nextThreshold, levelName) = currentLevelTier

// Déterminer le nom du prochain niveau
        val nextLevelIndex = levelTiers.indexOf(currentLevelTier) + 1
        val nextLevelTier = levelTiers.getOrNull(nextLevelIndex)
        val nextLevelName = nextLevelTier?.third ?: "Légende Littéraire"

// Calcul de la progression
        val progressRange = nextThreshold - previousThreshold
        val currentProgressInLevel = livresLusCompteur - previousThreshold

// Pourcentage de remplissage
        val progressPercent = if (nextLevelTier != null) {
            if (progressRange > 0) {
                min(100, ((currentProgressInLevel.toFloat() / progressRange.toFloat()) * 100).toInt())
            } else {
                100
            }
        } else {
            100
        }

// Mettre à jour l'UI
        binding.levelName.text = levelName
        binding.levelProgressBar.progress = progressPercent

// ✅ CAS SPÉCIAL pour 0 livres
        if (livresLusCompteur == 0) {
            binding.progressText.text = "📚 Ajoutez votre premier livre pour commencer !"
        } else if (nextLevelTier != null) {
            binding.progressText.text = "$livresLusCompteur/$nextThreshold livres lus pour passer au niveau '$nextLevelName' !"
        } else {
            // Niveau maximum atteint (40 livres ou au-delà)
            val finalLevelName = "Légende Littéraire"
            binding.levelName.text = finalLevelName
            binding.progressText.text = "🎉 Félicitations ! Vous avez atteint le niveau $finalLevelName !"
        }
    }

    // ==========================================================
    // Badge gestion
    // ==========================================================

    private fun chargerBadges() {
        Log.d("AccountFragment", "chargerBadges() appelée")

        if (!connectionManager.isLoggedIn()) {
            Log.e("AccountFragment", "Pas connecté")
            return
        }

        lifecycleScope.launch {
            Log.d("AccountFragment", "Appel API badges...")
            val result = connectionManager.getBadges()

            result.onSuccess { badgesResponse ->
                Log.d("AccountFragment", "${badgesResponse.badges.size} badges reçus")
                Log.d("AccountFragment", "Total livres lus: ${badgesResponse.totalBooksRead}")

                // LOG DÉTAILLÉ de chaque badge
                badgesResponse.badges.forEachIndexed { index, badge ->
                    Log.d("AccountFragment", "Badge #${index + 1}:")
                    Log.d("AccountFragment", "  - Nom: ${badge.name}")
                    Log.d("AccountFragment", "  - Unlocked: ${badge.isUnlocked}")
                    Log.d("AccountFragment", "  - Progress: ${badge.progress}%")
                    Log.d("AccountFragment", "  - URL: ${badge.imageUrl}")
                }

                // Configuration du RecyclerView
                Log.d("AccountFragment", "🔧 Configuration du RecyclerView...")
                binding.recyclerBadges.layoutManager = androidx.recyclerview.widget.GridLayoutManager(
                    requireContext(),
                    3
                )

                badgesAdapter = BadgesAdapter(badgesResponse.badges)
                binding.recyclerBadges.adapter = badgesAdapter

                binding.recyclerBadges.post {
                    binding.recyclerBadges.requestLayout()
                }
                Log.d("AccountFragment", "RecyclerView configuré avec adapter de ${badgesResponse.badges.size} items")
                Log.d("AccountFragment", "RecyclerView visibility: ${binding.recyclerBadges.visibility}")
            }

            result.onFailure { error ->
                Log.e("AccountFragment", "Erreur badges: ${error.message}")
                error.printStackTrace()
            }
        }
    }
}