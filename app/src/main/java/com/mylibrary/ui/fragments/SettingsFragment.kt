package com.mylibrary.ui.fragments

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.mylibrary.R
import com.mylibrary.network.BookRepository
import com.mylibrary.network.ConnectionManager
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    // VUES
    private var btnLogout: Button? = null
    private var btnDeleteAccount: Button? = null
    private var layoutTheme: LinearLayout? = null
    private var tvCurrentTheme: TextView? = null
    private var layoutProfilePicture: LinearLayout? = null
    private var layoutEmail: LinearLayout? = null
    private var layoutPassword: LinearLayout? = null

    private var auth: FirebaseAuth? = null
    private lateinit var connectionManager: ConnectionManager
    private lateinit var bookRepository: BookRepository

    private var layoutUsername: LinearLayout? = null

    companion object {
        const val PREFS_NAME = "app_preferences"
        const val KEY_SELECTED_THEME = "selected_theme"
        const val KEY_SELECTED_PP = "selected_profile_picture"
    }

    private val profilePictures = mapOf(
        "pp_pandas" to R.drawable.pp_pandas,
        "pp_chian" to R.drawable.pp_chien,
        "pp_chat" to R.drawable.pp_chat,
        "pp_hiboux" to R.drawable.pp_hiboux,
        "pp_renne" to R.drawable.pp_renne,
        "Défaut" to R.drawable.ic_launcher_foreground
    )

    // ===============================================
    // Gestionnaire de Photo de Profil
    // ===============================================

    object ProfilePictureManager {
        fun saveProfilePictureName(context: Context, ppName: String) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_SELECTED_PP, ppName).apply()
        }

        fun getSavedProfilePictureName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_SELECTED_PP, "pp_hiboux") ?: "pp_hiboux"
        }

        fun getProfilePictureId(ppMap: Map<String, Int>, ppName: String): Int {
            return ppMap[ppName] ?: ppMap["Défaut"]!!
        }
    }

    // ===============================================
    // Gestionnaire de Thème
    // ===============================================

    object ThemeManager {
        const val THEME_CLAIR = 0
        const val THEME_POMME = 1
        const val THEME_ROSE = 2
        const val THEME_SOMBRE = 3

        fun saveTheme(context: Context, theme: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY_SELECTED_THEME, theme).apply()
        }

        fun getSavedTheme(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_SELECTED_THEME, THEME_CLAIR)
        }

        fun applyTheme(context: Context, theme: Int) {
            when (theme) {
                THEME_CLAIR -> context.setTheme(R.style.AppTheme)
                THEME_POMME -> context.setTheme(R.style.AppTheme_Pomme)
                THEME_ROSE -> context.setTheme(R.style.AppTheme_Rose)
                THEME_SOMBRE -> context.setTheme(R.style.AppTheme_Sombre)
            }
        }

        fun getThemeName(theme: Int): String {
            return when (theme) {
                THEME_CLAIR -> "Clair"
                THEME_POMME -> "Pomme"
                THEME_ROSE -> "Rose"
                THEME_SOMBRE -> "Sombre"
                else -> "Clair"
            }
        }
    }

    // ===============================================
    // Cycle de Vie du Fragment
    // ===============================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        connectionManager = ConnectionManager.getInstance(requireContext())
        bookRepository = BookRepository.getInstance(requireContext())

        try {
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            auth = null
        }

        // Initialiser les vues
        btnLogout = view.findViewById(R.id.btnLogout)
        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount)
        layoutTheme = view.findViewById(R.id.layoutTheme)
        tvCurrentTheme = view.findViewById(R.id.tvCurrentTheme)
        layoutProfilePicture = view.findViewById(R.id.layoutProfilePicture)
        layoutEmail = view.findViewById(R.id.layoutEmail)
        layoutPassword = view.findViewById(R.id.layoutPassword)
        layoutUsername = view.findViewById(R.id.layoutUsername)

        // Afficher le thème actuel
        updateCurrentThemeDisplay()
        // Afficher le nom de la PP actuelle
        updateCurrentPPDisplay(view)

        // Configuration des clics
        btnLogout?.setOnClickListener { showLogoutDialog() }
        btnDeleteAccount?.setOnClickListener { showDeleteAccountDialog() }
        layoutTheme?.setOnClickListener { showThemeDialog() }
        layoutProfilePicture?.setOnClickListener { showProfilePictureDialog() }
        layoutEmail?.setOnClickListener { showEmailDialog() }
        layoutPassword?.setOnClickListener { showPasswordDialog() }
        layoutUsername?.setOnClickListener { showUsernameDialog() }

        return view
    }

    // ===============================================
// Fonction MODIFIER NOM D'UTILISATEUR
// ===============================================

    private fun showUsernameDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_username, null)
        val etNewUsername = dialogView.findViewById<EditText>(R.id.etNewUsername)

        // Charger le username actuel
        lifecycleScope.launch {
            val result = bookRepository.getUserProfile()
            result.onSuccess { profile ->
                etNewUsername.setText(profile.username)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Modifier le nom d'utilisateur")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newUsername = etNewUsername.text.toString()

                if (newUsername.isBlank()) {
                    Toast.makeText(context, "Le nom d'utilisateur ne peut pas être vide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Appeler l'API
                lifecycleScope.launch {
                    val result = bookRepository.updateUserProfile(username = newUsername, email = null)

                    result.onSuccess { profile ->
                        Toast.makeText(context, "Nom d'utilisateur modifié avec succès !", Toast.LENGTH_SHORT).show()
                    }

                    result.onFailure { error ->
                        Toast.makeText(context, "Erreur : ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ===============================================
    // Fonction DÉCONNEXION
    // ===============================================

    private fun showLogoutDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Déconnexion")
            .setMessage("Êtes-vous sûr de vouloir vous déconnecter ?")
            .setPositiveButton("Oui") { _, _ ->
                // Déconnexion Firebase (si utilisé)
                auth?.signOut()

                // Effacer le token JWT
                connectionManager.clearToken()

                Log.d("SettingsFragment", "Utilisateur déconnecté")
                Toast.makeText(context, "Déconnecté avec succès", Toast.LENGTH_SHORT).show()

                // Rediriger vers l'écran de connexion
                findNavController().navigate(R.id.loginFragment)
            }
            .setNegativeButton("Non", null)
            .show()
    }

    // ===============================================
    // Fonction SUPPRIMER LE COMPTE
    // ===============================================

    private fun showDeleteAccountDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_password_confirmation, null)
        val etPassword = dialogView.findViewById<EditText>(R.id.etPassword)

        AlertDialog.Builder(requireContext())
            .setTitle("⚠️ Supprimer le compte")
            .setMessage("Cette action est IRRÉVERSIBLE. Tous vos livres seront supprimés.\n\nEntrez votre mot de passe pour confirmer :")
            .setView(dialogView)
            .setPositiveButton("Supprimer") { _, _ ->
                val password = etPassword.text.toString()

                if (password.isBlank()) {
                    Toast.makeText(context, "Veuillez entrer votre mot de passe", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Appeler l'API
                lifecycleScope.launch {
                    val result = bookRepository.deleteAccount(password)

                    result.onSuccess { message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()

                        // Déconnexion et redirection
                        auth?.signOut()
                        connectionManager.clearToken()
                        findNavController().navigate(R.id.loginFragment)
                    }

                    result.onFailure { error ->
                        Toast.makeText(context, "Erreur : ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ===============================================
    // Fonction THÈME
    // ===============================================

    private fun updateCurrentThemeDisplay() {
        val currentTheme = ThemeManager.getSavedTheme(requireContext())
        val themeName = ThemeManager.getThemeName(currentTheme)
        tvCurrentTheme?.text = "$themeName ›"
    }

    private fun showThemeDialog() {
        val themes = arrayOf("Clair", "Pomme", "Rose", "Sombre")
        val currentTheme = ThemeManager.getSavedTheme(requireContext())

        AlertDialog.Builder(requireContext())
            .setTitle("Choisir un thème")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                // Sauvegarder le nouveau thème
                ThemeManager.saveTheme(requireContext(), which)

                // Mettre à jour l'affichage du thème actuel
                updateCurrentThemeDisplay()

                Toast.makeText(context, "Thème changé en ${themes[which]}", Toast.LENGTH_SHORT).show()

                // Redémarrer l'activité pour appliquer le thème
                requireActivity().recreate()

                dialog.dismiss()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ===============================================
    // Fonction PHOTO DE PROFIL
    // ===============================================

    private fun updateCurrentPPDisplay(view: View) {
        val tvCurrentPP = view.findViewById<TextView>(R.id.tvCurrentPP)
        val currentPPName = ProfilePictureManager.getSavedProfilePictureName(requireContext())
        tvCurrentPP?.text = "$currentPPName ›"
    }

    private fun showProfilePictureDialog() {
        val ppNames = profilePictures.keys.toList()
        val builder = AlertDialog.Builder(requireContext())

        // Utiliser le layout personnalisé pour le dialogue
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_profile_selector, null)
        val container = dialogView.findViewById<LinearLayout>(R.id.pp_container)

        // Récupérer la taille et l'espacement
        val size = resources.getDimensionPixelSize(R.dimen.pp_preview_size)
        val spacing = resources.getDimensionPixelSize(R.dimen.pp_preview_spacing)

        // Ajouter chaque PP dans le conteneur
        ppNames.forEach { name ->
            val ppImageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = spacing
                }
                setImageResource(profilePictures[name]!!)
                scaleType = ImageView.ScaleType.CENTER_CROP

                // Mettre le fond d'une PP sélectionnée
                if (ProfilePictureManager.getSavedProfilePictureName(requireContext()) == name) {
                    setBackgroundResource(R.drawable.pp_selector_border)
                } else {
                    background = null
                }

                setOnClickListener {
                    ProfilePictureManager.saveProfilePictureName(requireContext(), name)
                    Toast.makeText(context, "Photo de profil changée en $name!", Toast.LENGTH_SHORT).show()

                    // Mettre à jour le texte dans SettingsFragment
                    view?.let { updateCurrentPPDisplay(it) }

                    // Fermer le dialogue
                    (parent as? ViewGroup)?.let { parent ->
                        (parent.parent as? AlertDialog)?.dismiss()
                    }
                }
            }
            container.addView(ppImageView)
        }

        builder.setView(dialogView)
        builder.create().show()
    }

    // ===============================================
    // Fonction MODIFIER EMAIL
    // ===============================================

    private fun showEmailDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_email, null)
        val etNewEmail = dialogView.findViewById<EditText>(R.id.etNewEmail)

        // Charger l'email actuel
        lifecycleScope.launch {
            val result = bookRepository.getUserProfile()
            result.onSuccess { profile ->
                etNewEmail.setText(profile.email)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Modifier l'email")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val newEmail = etNewEmail.text.toString()

                if (newEmail.isBlank()) {
                    Toast.makeText(context, "L'email ne peut pas être vide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Appeler l'API
                lifecycleScope.launch {
                    val result = bookRepository.updateUserProfile(username = null, email = newEmail)

                    result.onSuccess { profile ->
                        Toast.makeText(context, "Email modifié avec succès !", Toast.LENGTH_SHORT).show()
                    }

                    result.onFailure { error ->
                        Toast.makeText(context, "Erreur : ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    // ===============================================
    // Fonction CHANGER MOT DE PASSE
    // ===============================================

    private fun showPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.etConfirmPassword)

        AlertDialog.Builder(requireContext())
            .setTitle("Changer le mot de passe")
            .setView(dialogView)
            .setPositiveButton("Enregistrer") { _, _ ->
                val currentPassword = etCurrentPassword.text.toString()
                val newPassword = etNewPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                // Validation
                if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
                    Toast.makeText(context, "Tous les champs sont requis", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword != confirmPassword) {
                    Toast.makeText(context, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (newPassword.length < 6) {
                    Toast.makeText(context, "Le mot de passe doit contenir au moins 6 caractères", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Appeler l'API
                lifecycleScope.launch {
                    val result = bookRepository.updatePassword(currentPassword, newPassword)

                    result.onSuccess { message ->
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }

                    result.onFailure { error ->
                        Toast.makeText(context, "Erreur : ${error.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}