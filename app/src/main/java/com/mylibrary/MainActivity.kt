package com.mylibrary

import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mylibrary.databinding.ActivityMainBinding
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager
import com.mylibrary.ui.adapters.ThemeManager
import kotlinx.coroutines.launch
import coil.load
import android.view.View

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bookRepository: BookRepository
    private lateinit var connectionManager: ConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        // Récupérer et appliquer le thème
        val savedTheme = ThemeManager.getSavedTheme(this)
        setTheme(ThemeManager.applyTheme(savedTheme))

        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialiser les managers
        connectionManager = ConnectionManager.getInstance(this)
        bookRepository = BookRepository.getInstance(this)

        // Ajouter des marges/padding au layout principal
        val paddingInDp = 16
        val scale = resources.displayMetrics.density
        val paddingInPx = (paddingInDp * scale).toInt()

        // Ajouter du padding (espace intérieur)
        binding.root.setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)
    }

    override fun onResume() {
        super.onResume()

        // Vérifier le level up à chaque fois que l'activité reprend
        if (connectionManager.isLoggedIn()) {
            verifierLevelUp()
        }
    }

    /**
     * Méthode publique pour vérifier le level up depuis n'importe quel fragment
     */
    fun verifierLevelUp() {
        lifecycleScope.launch {
            val result = bookRepository.getAllMyBooks()

            result.onSuccess { livres ->
                val livresLus = livres.count { it.status == "lu" }

                // Définir les paliers (MÊMES que dans AccountFragment)
                val levelTiers = listOf(
                    Triple(1, 3, "Débutant"),
                    Triple(3, 5, "Novice"),
                    Triple(5, 10, "Amateur"),
                    Triple(10, 20, "Passionné"),
                    Triple(20, 30, "Expert"),
                    Triple(30, 40, "Maître"),
                    Triple(40, 999, "Légende Littéraire")
                )

                // Trouver le niveau actuel
                val currentLevelTier = levelTiers.lastOrNull { livresLus >= it.first } ?: levelTiers.first()
                val levelName = currentLevelTier.third

                // Vérifier si le niveau a changé
                val sharedPrefs = getSharedPreferences("MyLibraryPrefs", MODE_PRIVATE)
                val lastLevel = sharedPrefs.getString("last_level", null)

                // Si c'est la première fois, on initialise sans popup
                if (lastLevel == null) {
                    sharedPrefs.edit().putString("last_level", levelName).apply()
                    return@onSuccess
                }

                // Si le niveau a changé
                if (levelName != lastLevel) {
                    val lastLevelIndex = levelTiers.indexOfFirst { it.third == lastLevel }
                    val currentLevelIndex = levelTiers.indexOfFirst { it.third == levelName }

                    // Si niveau supérieur (pas inférieur si jamais on supprime des livres)
                    if (currentLevelIndex > lastLevelIndex) {
                        // Sauvegarder le nouveau niveau
                        sharedPrefs.edit().putString("last_level", levelName).apply()

                        // Afficher la popup
                        showLevelUpDialog(levelName, livresLus)
                    }
                }
            }
        }
    }

    /**
     * Afficher le dialog de niveau supérieur
     */
    private fun showLevelUpDialog(newLevel: String, booksRead: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_level_up, null)

        val tvNewLevel = dialogView.findViewById<TextView>(R.id.tvNewLevel)
        val tvLevelMessage = dialogView.findViewById<TextView>(R.id.tvLevelMessage)
        val ivBadge = dialogView.findViewById<ImageView>(R.id.ivBadge)
        val btnContinue = dialogView.findViewById<Button>(R.id.btnContinue)

        tvNewLevel.text = newLevel
        tvLevelMessage.text = "Vous avez lu $booksRead livres !"

        // Charger l'image du badge depuis le backend
        lifecycleScope.launch {
            val result = connectionManager.getBadges()

            result.onSuccess { badgesResponse ->
                // Trouver le badge qui correspond au niveau atteint
                val badge = badgesResponse.badges.find {
                    it.name.equals(newLevel, ignoreCase = true)
                }

                if (badge != null && badge.imageUrl.isNotEmpty()) {
                    // Charger l'image avec Coil
                    ivBadge.load(badge.imageUrl)
                    ivBadge.visibility = View.VISIBLE
                } else {
                    // Masquer l'image si pas de badge trouvé
                    ivBadge.visibility = View.GONE
                }
            }

            result.onFailure {
                // En cas d'erreur, masquer l'image
                ivBadge.visibility = View.GONE
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnContinue.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }}