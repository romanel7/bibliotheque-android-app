package com.mylibrary.network

import android.content.Context
import android.util.Log
import com.mylibrary.model.BookRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiApiManager private constructor(context: Context) {


    private val connectionManager = ConnectionManager.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    //private val baseUrl = "http://10.0.2.2:3000/api/ai"
    private val baseUrl = "http://192.168.1.209:3000/api/ai"

    companion object {
        @Volatile
        private var instance: AiApiManager? = null

        fun getInstance(context: Context): AiApiManager {
            return instance ?: synchronized(this) {
                instance ?: AiApiManager(context.applicationContext).also { instance = it }
            }
        }
    }

    suspend fun getRecommendations(): Result<List<BookRecommendation>> = withContext(Dispatchers.IO) {
        try {
            val token = connectionManager.getToken()
                ?: return@withContext Result.failure(Exception("Non connecté"))

            val request = Request.Builder()
                .url("$baseUrl/recommendations")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val recommendationsArray = json.getJSONArray("recommendations")

                val recommendations = mutableListOf<BookRecommendation>()
                for (i in 0 until recommendationsArray.length()) {
                    val item = recommendationsArray.getJSONObject(i)
                    recommendations.add(
                        BookRecommendation(
                            title = item.getString("title"),
                            author = item.getString("author"),
                            isbn = item.optString("isbn").takeIf { it.isNotEmpty() },
                            reason = item.getString("reason")
                        )
                    )
                }

                Result.success(recommendations)
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("AiApiManager", "Erreur", e)
            Result.failure(e)
        }
    }

    suspend fun getBookSummary(bookId: Int): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = connectionManager.getToken()
                ?: return@withContext Result.failure(Exception("Non connecté"))

            val request = Request.Builder()
                .url("$baseUrl/summary/$bookId")
                .addHeader("Authorization", "Bearer $token")
                .post(okhttp3.RequestBody.create(null, ""))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d("AiApiManager", "Résumé - Code: ${response.code}")

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                val summary = json.getString("summary")

                Log.d("AiApiManager", "Résumé chargé")
                Result.success(summary)
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("AiApiManager", "Erreur résumé", e)
            Result.failure(e)
        }
    }
}