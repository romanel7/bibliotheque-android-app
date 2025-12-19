package com.mylibrary.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.mylibrary.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RegisterFragment : Fragment() {

    private lateinit var usernameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var registerButton: Button
    private lateinit var loginLink: TextView

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_register, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialiser les vues
        usernameInput = view.findViewById(R.id.username_input)
        emailInput = view.findViewById(R.id.email_input)
        passwordInput = view.findViewById(R.id.password_input)
        confirmPasswordInput = view.findViewById(R.id.confirm_password_input)
        registerButton = view.findViewById(R.id.register_button)
        loginLink = view.findViewById(R.id.login_link)

        // Bouton inscription
        registerButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            // Validation
            when {
                username.isEmpty() || email.isEmpty() || password.isEmpty() -> {
                    Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                }
                !email.contains("@") -> {
                    Toast.makeText(context, "Email invalide", Toast.LENGTH_SHORT).show()
                }
                password.length < 6 -> {
                    Toast.makeText(context, "Le mot de passe doit contenir au moins 6 caractères", Toast.LENGTH_SHORT).show()
                }
                password != confirmPassword -> {
                    Toast.makeText(context, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    register(username, email, password)
                }
            }
        }

        // Lien vers connexion
        loginLink.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun register(username: String, email: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("username", username)
                    put("email", email)
                    put("password", password)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("http://192.168.1.209:3000/api/register")
                    .post(requestBody)
                    .build()


                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        val responseJson = JSONObject(responseBody)
                        val token = responseJson.getString("token")
                        val userJson = responseJson.getJSONObject("user")

                        // Sauvegarder le token et les infos utilisateur
                        saveUserSession(
                            token,
                            userJson.getInt("id"),
                            userJson.getString("username"),
                            userJson.getString("email")
                        )

                        Toast.makeText(context, "Inscription réussie ! Bienvenue ${username} !", Toast.LENGTH_LONG).show()

                        // Naviguer vers l'accueil
                        findNavController().navigate(R.id.action_registerFragment_to_FirstFragment)
                    } else {
                        val errorMessage = if (responseBody != null) {
                            try {
                                JSONObject(responseBody).getString("error")
                            } catch (e: Exception) {
                                "Erreur lors de l'inscription"
                            }
                        } else {
                            "Erreur lors de l'inscription"
                        }

                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Erreur réseau. Vérifiez que le serveur est démarré.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun saveUserSession(token: String, userId: Int, username: String, email: String) {
        val sharedPref = requireActivity().getSharedPreferences("MyLibraryPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("auth_token", token)
            putInt("user_id", userId)
            putString("username", username)
            putString("email", email)
            apply()
        }
    }
}