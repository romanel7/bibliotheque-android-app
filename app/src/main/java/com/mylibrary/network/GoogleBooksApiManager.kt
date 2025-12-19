package com.mylibrary.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class GoogleBooksApiManager private constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)  // Timeout connexion
        .readTimeout(15, TimeUnit.SECONDS)      // Timeout lecture
        .writeTimeout(15, TimeUnit.SECONDS)     // Timeout écriture
        .build()

    private val BASE_URL = "https://www.googleapis.com/books/v1"

    companion object {
        @Volatile
        private var INSTANCE: GoogleBooksApiManager? = null

        fun getInstance(): GoogleBooksApiManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoogleBooksApiManager().also { INSTANCE = it }
            }
        }
    }

    /**
     * Rechercher des livres sur Google Books
     * @param query La requête de recherche (titre, auteur, etc.)
     * @return ApiResponse contenant les résultats
     */
    suspend fun searchBooks(query: String, maxResults: Int = 20): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/volumes?q=$query&maxResults=$maxResults"

                Log.d("GoogleBooksAPI", "Recherche: $query")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                Log.d("GoogleBooksAPI", "Réponse: code=${response.code}")

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: UnknownHostException) {
                Log.e("GoogleBooksAPI", "Impossible de résoudre l'hôte (pas de connexion Internet ?)")
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = "Pas de connexion Internet"
                )
            } catch (e: SocketTimeoutException) {
                Log.e("GoogleBooksAPI", "Timeout - Google Books met trop de temps à répondre")
                ApiResponse(
                    isSuccessful = false,
                    code = -2,
                    body = null,
                    errorMessage = "Timeout"
                )
            } catch (e: IOException) {
                Log.e("GoogleBooksAPI", "Erreur réseau: ${e.message}")
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                Log.e("GoogleBooksAPI", "Erreur inattendue: ${e.message}", e)
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
     * Rechercher un livre par ISBN
     * @param isbn Le numéro ISBN (ex: "9780451524935")
     * @return ApiResponse contenant le résultat
     */
    suspend fun searchByISBN(isbn: String): ApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/volumes?q=isbn:$isbn"

                Log.d("GoogleBooksAPI", "🔎 Recherche ISBN: $isbn")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                Log.d("GoogleBooksAPI", "Réponse ISBN: code=${response.code}")

                ApiResponse(
                    isSuccessful = response.isSuccessful,
                    code = response.code,
                    body = body
                )
            } catch (e: UnknownHostException) {
                Log.e("GoogleBooksAPI", "Impossible de résoudre l'hôte (pas de connexion Internet ?)")
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = "Pas de connexion Internet"
                )
            } catch (e: SocketTimeoutException) {
                Log.e("GoogleBooksAPI", "Timeout - Google Books met trop de temps à répondre")
                ApiResponse(
                    isSuccessful = false,
                    code = -2,
                    body = null,
                    errorMessage = "Timeout"
                )
            } catch (e: IOException) {
                Log.e("GoogleBooksAPI", "Erreur réseau: ${e.message}")
                ApiResponse(
                    isSuccessful = false,
                    code = -1,
                    body = null,
                    errorMessage = e.message
                )
            } catch (e: Exception) {
                Log.e("GoogleBooksAPI", "Erreur inattendue: ${e.message}", e)
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