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

class LoginFragment : Fragment() {

    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var registerLink: TextView

    private val client = OkHttpClient()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Vérifier si déjà connecté
        checkIfAlreadyLoggedIn()

        // Initialiser les vues
        usernameInput = view.findViewById(R.id.username_input)
        passwordInput = view.findViewById(R.id.password_input)
        loginButton = view.findViewById(R.id.login_button)
        registerLink = view.findViewById(R.id.register_link)

        // Bouton connexion
        loginButton.setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val password = passwordInput.text.toString()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            login(username, password)
        }

        // Lien vers inscription
        registerLink.setOnClickListener {
            findNavController().navigate(R.id.action_loginFragment_to_registerFragment)
        }
    }

    private fun checkIfAlreadyLoggedIn() {
        val sharedPref = requireActivity().getSharedPreferences("MyLibraryPrefs", Context.MODE_PRIVATE)
        val token = sharedPref.getString("auth_token", null)

        if (!token.isNullOrEmpty()) {
            // Déjà connecté, aller à l'accueil
            findNavController().navigate(R.id.action_loginFragment_to_FirstFragment)
        }
    }

    private fun login(username: String, password: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }

                val requestBody = json.toString().toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("http://192.168.1.209:3000/api/login")
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

                        Toast.makeText(context, "Connexion réussie !", Toast.LENGTH_SHORT).show()

                        // Naviguer vers l'accueil
                        findNavController().navigate(R.id.action_loginFragment_to_FirstFragment)
                    } else {
                        val errorMessage = if (responseBody != null) {
                            try {
                                JSONObject(responseBody).getString("error")
                            } catch (e: Exception) {
                                "Erreur de connexion"
                            }
                        } else {
                            "Erreur de connexion"
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