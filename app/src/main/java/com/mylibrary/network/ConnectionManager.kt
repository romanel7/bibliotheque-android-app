package com.mylibrary.network

import android.content.Context
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.mylibrary.model.Badge
import com.mylibrary.model.BadgesResponse
import java.io.IOException

class ConnectionManager private constructor(context: Context) {

    private val sharedPreferences = context.getSharedPreferences("MyLibraryPrefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    // URL de base de l'API
    // sur mon ordi :
    // private val BASE_URL = "http://10.0.2.2:3000/api"
    // sur tel :
    // private val BASE_URL = "http://192.168.2.223:3000/api"
    // ca change verifier mon ipv4 dans le PowerShelle avec la commande : ipconfig
    // enlever le parfeu winndows defender pour les réseaux public


    private val BASE_URL = "http:/192.168.1.209:3000/api"
    private val IMAGE_BASE_URL = "http://192.168.1.209:3000"


    companion object {
        @Volatile
        private var INSTANCE: ConnectionManager? = null

        fun getInstance(context: Context): ConnectionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ConnectionManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // Récupérer le token
    fun getToken(): String? {
        return sharedPreferences.getString("auth_token", null)
    }

    // Sauvegarder le token
    fun saveToken(token: String) {
        sharedPreferences.edit().putString("auth_token", token).apply()
    }

    // Supprimer le token (logout)
    fun clearToken() {
        sharedPreferences.edit().clear().apply()
    }

    // Vérifier si l'utilisateur est connecté
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    /**
     * Méthode DELETE avec body (pour suppression de compte)
     */
    suspend fun deleteWithBody(endpoint: String, jsonBody: String, requiresAuth: Boolean = true): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .delete(jsonBody.toRequestBody("application/json".toMediaType()))

                if (requiresAuth) {
                    val token = getToken()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: IOException) {
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Méthode générique pour faire une requête GET
     * @param endpoint Le endpoint de l'API (ex: "/livres")
     * @param requiresAuth Si true, ajoute le token d'authentification
     * @return ApiResponse contenant le code, le succès et le body
     */
    suspend fun get(endpoint: String, requiresAuth: Boolean = true): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .get()

                if (requiresAuth) {
                    val token = getToken()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: IOException) {
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            }
        }
    }


    suspend fun getBadges(): Result<BadgesResponse> = withContext(Dispatchers.IO) {
        try {
            val token = getToken() ?: return@withContext Result.failure(Exception("Non connecté"))

            val request = Request.Builder()
                .url("$BASE_URL/badges")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val badgesArray = json.getJSONArray("badges")
                val totalBooksRead = json.getInt("totalBooksRead")

                val badges = mutableListOf<Badge>()
                for (i in 0 until badgesArray.length()) {
                    val badgeJson = badgesArray.getJSONObject(i)
                    badges.add(
                        Badge(
                            id = badgeJson.getInt("id"),
                            name = badgeJson.getString("name"),
                            minBooks = badgeJson.getInt("minBooks"),
                            maxBooks = badgeJson.getInt("maxBooks"),
                            imageUrl = IMAGE_BASE_URL + badgeJson.getString("imageUrl"),
                            isUnlocked = badgeJson.getBoolean("isUnlocked"),
                            progress = badgeJson.getInt("progress"),
                            booksNeeded = badgeJson.getInt("booksNeeded")
                        )
                    )
                }

                Result.success(BadgesResponse(badges, totalBooksRead))
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("ConnectionManager", "Erreur badges", e)
            Result.failure(e)
        }
    }

    /**
     * Méthode générique pour faire une requête POST
     * @param endpoint Le endpoint de l'API (ex: "/livres")
     * @param jsonBody Le corps de la requête en JSON
     * @param requiresAuth Si true, ajoute le token d'authentification
     * @return ApiResponse contenant le code, le succès et le body
     */
    suspend fun post(endpoint: String, jsonBody: String, requiresAuth: Boolean = true): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))

                if (requiresAuth) {
                    val token = getToken()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: IOException) {
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Méthode générique pour faire une requête PUT
     * @param endpoint Le endpoint de l'API (ex: "/livres/1")
     * @param jsonBody Le corps de la requête en JSON
     * @param requiresAuth Si true, ajoute le token d'authentification
     * @return ApiResponse contenant le code, le succès et le body
     */
    suspend fun put(endpoint: String, jsonBody: String, requiresAuth: Boolean = true): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .put(jsonBody.toRequestBody("application/json".toMediaType()))

                if (requiresAuth) {
                    val token = getToken()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: IOException) {
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Méthode générique pour faire une requête DELETE
     * @param endpoint Le endpoint de l'API (ex: "/livres/1")
     * @param requiresAuth Si true, ajoute le token d'authentification
     * @return ApiResponse contenant le code, le succès et le body
     */
    suspend fun delete(endpoint: String, requiresAuth: Boolean = true): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$BASE_URL$endpoint")
                    .delete()

                if (requiresAuth) {
                    val token = getToken()
                    if (token != null) {
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()
                val body = response.body?.string()

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: IOException) {
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * Classe de données pour encapsuler la réponse de l'API
     */
    data class ApiResponse(
        val isSuccessful: Boolean,
        val code: Int,
        val body: String?,
        val errorMessage: String? = null
    )
}

