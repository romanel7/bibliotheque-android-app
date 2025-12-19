package com.mylibrary.network

import android.content.Context
import com.mylibrary.model.Book
import com.mylibrary.model.SearchResult
import com.mylibrary.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext



class BookRepository private constructor(context: Context) {

    // Utilise les DEUX managers
    private val backendManager = ConnectionManager.getInstance(context)
    private val googleBooksManager = GoogleBooksApiManager.getInstance()

    companion object {
        @Volatile
        private var INSTANCE: BookRepository? = null

        fun getInstance(context: Context): BookRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BookRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ==========================================
    // Le SERVEUR (via ConnectionManager)
    // ==========================================

    // fonction qui retourne la liste de mes livres
    suspend fun getAllMyBooks(): Result<List<Book>> {
        return try {
            val response = backendManager.get("/livres")

            if (response.isSuccessful && response.body != null) {
                val books = parseMyBooks(response.body)
                Result.success(books)
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // fonction qui retourne un seul livre par son ID
    suspend fun getBookById(bookId: Int): Result<Book> {
        return try {
            // Fait un appel GET à /livres/{id}
            val response = backendManager.get("/livres/$bookId")

            if (response.isSuccessful && response.body != null) {
                // Utilise ton parser existant
                val book = parseBook(JSONObject(response.body))
                Result.success(book)
            } else {
                Result.failure(Exception("Livre non trouvé (Erreur ${response.code})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ===============================================
// GESTION DU PROFIL UTILISATEUR
// ===============================================

    /**
     * Récupérer le profil utilisateur
     */
    suspend fun getUserProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val response = backendManager.get("/profile", requiresAuth = true)

                if (response.isSuccessful && response.body != null) {
                    val json = JSONObject(response.body)
                    val profile = UserProfile(
                        id = json.getInt("id"),
                        username = json.getString("username"),
                        email = json.getString("email")
                    )
                    Result.success(profile)
                } else {
                    Result.failure(Exception("Erreur ${response.code}: ${response.body}"))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Erreur getUserProfile", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Modifier le profil (username et/ou email)
     */
    suspend fun updateUserProfile(username: String?, email: String?): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject()
                if (username != null) json.put("username", username)
                if (email != null) json.put("email", email)

                val response = backendManager.put("/profile", json.toString(), requiresAuth = true)

                if (response.isSuccessful && response.body != null) {
                    val responseJson = JSONObject(response.body)
                    val userJson = responseJson.getJSONObject("user")
                    val profile = UserProfile(
                        id = userJson.getInt("id"),
                        username = userJson.getString("username"),
                        email = userJson.getString("email")
                    )
                    Result.success(profile)
                } else {
                    val errorJson = JSONObject(response.body ?: "{}")
                    val errorMsg = errorJson.optString("error", "Erreur ${response.code}")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Erreur updateUserProfile", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Changer le mot de passe
     */
    suspend fun updatePassword(currentPassword: String, newPassword: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("currentPassword", currentPassword)
                    put("newPassword", newPassword)
                }

                val response = backendManager.put("/profile/password", json.toString(), requiresAuth = true)

                if (response.isSuccessful && response.body != null) {
                    val responseJson = JSONObject(response.body)
                    val message = responseJson.getString("message")
                    Result.success(message)
                } else {
                    val errorJson = JSONObject(response.body ?: "{}")
                    val errorMsg = errorJson.optString("error", "Erreur ${response.code}")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Erreur updatePassword", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Supprimer le compte
     */
    suspend fun deleteAccount(password: String): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("password", password)
                }

                val response = backendManager.deleteWithBody("/profile", json.toString(), requiresAuth = true)

                if (response.isSuccessful && response.body != null) {
                    val responseJson = JSONObject(response.body)
                    val message = responseJson.getString("message")
                    Result.success(message)
                } else {
                    val errorJson = JSONObject(response.body ?: "{}")
                    val errorMsg = errorJson.optString("error", "Erreur ${response.code}")
                    Result.failure(Exception(errorMsg))
                }
            } catch (e: Exception) {
                Log.e("BookRepository", "Erreur deleteAccount", e)
                Result.failure(e)
            }
        }
    }

    // fonction qui ajoute un livre à la bibliothèque
    suspend fun addBookToMyLibrary(book: Book): Result<String> {
        Log.d("BookRepository", "📤 Ajout livre: ${book.titre}")
        Log.d("BookRepository", "📤 Catégories du livre: ${book.categories}")
        return try {
            val json = bookToJson(book)
            val response = backendManager.post("/livres", json)

            if (response.isSuccessful) {
                Result.success("Livre ajouté avec succès")
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // fonction qui supprime un livre
    suspend fun deleteBook(bookId: Int): Result<String> {
        return try {
            val response = backendManager.delete("/livres/$bookId")

            if (response.isSuccessful) {
                Result.success("Livre supprimé")
            } else {
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // fonction qui modifie un livre existant
    suspend fun updateBook(bookId: Int, book: Book): Result<String> {
        return try {
            val json = bookToJson(book)
            val response = backendManager.put("/livres/$bookId", json)

            if (response.isSuccessful) {
                Result.success("Livre modifié avec succès")
            } else {
                Result.failure(Exception("Erreur ${response.code}: ${response.errorMessage ?: "Erreur inconnue"}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Fonction qui modifie un livre (seulement status, note, memo)
    suspend fun updateBook(
        bookId: Int,
        status: String,
        note: Int,
        memo: String
    ): Result<String> {
        return try {
            //récupérer le livre complet
            val bookResult = getBookById(bookId)

            if (bookResult.isFailure) {
                return Result.failure(Exception("Impossible de charger le livre"))
            }

            val book = bookResult.getOrNull()!!

            // Créer le JSON avec TOUS les champs requis
            val json = JSONObject().apply {
                put("titre", book.titre)
                put("auteur", book.auteur)
                put("status", status)
                put("note", note)
                put("memo", memo)

                Log.d("BookRepository", "🔵 URL de base: Vérifie dans BackendManager")
                Log.d("BookRepository", "🔵 Endpoint appelé: /livres/$bookId")


                if (!book.isbn.isNullOrBlank()) put("isbn", book.isbn)
                if (!book.publisher.isNullOrBlank()) put("publisher", book.publisher)
                if (!book.publishedDate.isNullOrBlank()) put("published_date", book.publishedDate)
                if (book.pageCount != null && book.pageCount > 0) put("page_count", book.pageCount)
                if (!book.language.isNullOrBlank()) put("language", book.language)
                if (!book.imageUrl.isNullOrBlank()) put("image_url", book.imageUrl)

                // Catégories
                if (!book.categories.isNullOrEmpty()) {
                    val categoriesArray = JSONArray()
                    book.categories.forEach { categoriesArray.put(it) }
                    put("categories", categoriesArray)
                }
            }

            Log.d("BookRepository", "Mise à jour livre $bookId: $json")

            val response = backendManager.put("/livres/$bookId", json.toString())

            if (response.isSuccessful) {
                Result.success("Livre modifié")
            } else {
                Log.e("BookRepository", "Erreur ${response.code}: ${response.body}")
                Result.failure(Exception("Erreur ${response.code}"))
            }
        } catch (e: Exception) {
            Log.e("BookRepository", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    // ==========================================
    // GOOGLE BOOKS API (via GoogleBooksApiManager)
    // ==========================================

    suspend fun searchGoogleBooks(query: String): Result<List<SearchResult>> {
        Log.d("BookRepository", "🔎 Recherche Google Books: $query")
        return try {
            val response = googleBooksManager.searchBooks(query)
            Log.d("BookRepository", "📡 Réponse - Code: ${response.code}, Success: ${response.isSuccessful}")
            Log.d("BookRepository", "📡 Body length: ${response.body?.length ?: 0}")
            Log.d("BookRepository", "📡 Error message: ${response.errorMessage}")

            if (response.isSuccessful && response.body != null) {
                val results = parseGoogleBooksResponse(response.body)
                Log.d("BookRepository", "${results.size} résultats trouvés")
                Result.success(results)
            } else {
                Log.e("BookRepository", "Échec: ${response.errorMessage}")
                Result.failure(Exception(response.errorMessage ?: "Aucun résultat trouvé"))
            }
        } catch (e: Exception) {
            Log.e("BookRepository", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }
    suspend fun searchGoogleBooksByISBN(isbn: String): Result<SearchResult?> {
        return try {
            val response = googleBooksManager.searchByISBN(isbn)

            if (response.isSuccessful && response.body != null) {
                val results = parseGoogleBooksResponse(response.body)
                Result.success(results.firstOrNull())
            } else {
                Result.failure(Exception("ISBN non trouvé"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    // Conversion SearchResult → Book (pour ajouter à ta bibliothèque)
    fun convertSearchResultToBook(searchResult: SearchResult): Book {
        return Book(
            titre = searchResult.title,
            auteur = searchResult.authors,
            isbn = searchResult.isbn,
            imageUrl = searchResult.imageUrl,
            status = "à lire",
            categories = searchResult.categories,  // ✅ Liste complète
            publisher = searchResult.publisher,
            publishedDate = searchResult.publishedDate,
            pageCount = searchResult.pageCount,
            language = searchResult.language ?: "fr",
            averageRatingGoogle = searchResult.averageRating,
            id = null,
            note = null,
            memo = null,
            dateLecture = null,
            dateAjout = null,
            dateModification = null,
            userId = null
        )
    }


    // ==========================================
    // 🔧 PARSING - TON SERVEUR
    // ==========================================

    private fun parseMyBooks(jsonString: String): List<Book> {
        val books = mutableListOf<Book>()
        val jsonArray = JSONArray(jsonString)

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            books.add(parseBook(json))
        }

        return books
    }

    private fun parseBook(json: JSONObject): Book {
        // ✅ Parser le tableau de catégories
        val categoriesArray = json.optJSONArray("categories")
        val categories = if (categoriesArray != null && categoriesArray.length() > 0) {
            (0 until categoriesArray.length()).map { categoriesArray.getString(it) }
        } else null

        return Book(
            id = json.optInt("id", 0),
            titre = json.optString("titre", "Titre inconnu"),
            auteur = json.optString("auteur", "Auteur inconnu"),
            categories = categories,  // ✅ Liste
            isbn = json.optString("isbn").takeIf { it.isNotEmpty() },
            publisher = json.optString("publisher").takeIf { it.isNotEmpty() },
            publishedDate = json.optString("published_date").takeIf { it.isNotEmpty() },
            pageCount = if (json.has("page_count") && !json.isNull("page_count")) json.optInt("page_count") else null,
            language = json.optString("language").takeIf { it.isNotEmpty() },
            averageRatingGoogle = if (json.has("average_rating_google") && !json.isNull("average_rating_google")) {
                json.optDouble("average_rating_google").toFloat()
            } else null,
            note = if (json.has("note") && !json.isNull("note")) json.optInt("note") else null,
            status = json.optString("status", "à lire"),
            memo = json.optString("memo").takeIf { it.isNotEmpty() },
            imageUrl = json.optString("image_url").takeIf { it.isNotEmpty() },
            dateLecture = json.optString("date_lecture").takeIf { it.isNotEmpty() },
            dateAjout = json.optString("date_ajout").takeIf { it.isNotEmpty() },
            dateModification = json.optString("date_modification").takeIf { it.isNotEmpty() },
            userId = if (json.has("user_id")) json.optInt("user_id") else null
        )
    }
    private fun parseUserProfile(jsonString: String): UserProfile {
        val json = JSONObject(jsonString)

        return UserProfile(
            id = json.optInt("id", 0),
            username = json.optString("username", "Utilisateur"),
            email = json.optString("email", "")
        )
    }

    private fun bookToJson(book: Book): String {
        val json = JSONObject()

        json.put("titre", book.titre)
        json.put("auteur", book.auteur)

        // ✅ Envoyer le tableau de catégories
        book.categories?.let { cats ->
            val categoriesArray = JSONArray()
            cats.forEach { categoriesArray.put(it) }
            json.put("categories", categoriesArray)
        }

        book.isbn?.let { json.put("isbn", it) }
        book.publisher?.let { json.put("publisher", it) }
        book.publishedDate?.let { json.put("published_date", it) }
        book.pageCount?.let { json.put("page_count", it) }
        book.language?.let { json.put("language", it) }
        book.averageRatingGoogle?.let { json.put("average_rating_google", it) }
        book.note?.let { json.put("note", it) }
        json.put("status", book.status)
        book.memo?.let { json.put("memo", it) }
        book.imageUrl?.let { json.put("image_url", it) }
        book.dateLecture?.let { json.put("date_lecture", it) }

        return json.toString()
    }
    // ==========================================
    //  GOOGLE BOOKS API
    // ==========================================

    private fun parseGoogleBooksResponse(jsonString: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            val json = JSONObject(jsonString)
            val items = json.optJSONArray("items") ?: return emptyList()

            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val volumeInfo = item.optJSONObject("volumeInfo") ?: continue

                val title = volumeInfo.optString("title", "Titre inconnu")

                // Parser les auteurs
                val authorsArray = volumeInfo.optJSONArray("authors")
                val authors = if (authorsArray != null && authorsArray.length() > 0) {
                    (0 until authorsArray.length()).joinToString(", ") {
                        authorsArray.getString(it)
                    }
                } else "Auteur inconnu"

                // Parser l'ISBN
                val isbn = volumeInfo.optJSONArray("industryIdentifiers")?.let { identifiers ->
                    for (j in 0 until identifiers.length()) {
                        val id = identifiers.getJSONObject(j)
                        if (id.optString("type") == "ISBN_13") {
                            return@let id.optString("identifier")
                        }
                    }
                    null
                }

                val imageUrl = volumeInfo.optJSONObject("imageLinks")?.optString("thumbnail")
                val description = volumeInfo.optString("description")

                // ✅ Parser TOUTES les catégories
                val categoriesArray = volumeInfo.optJSONArray("categories")
                val categories = if (categoriesArray != null && categoriesArray.length() > 0) {
                    (0 until categoriesArray.length()).map { idx ->
                        // Simplifier la catégorie (prendre avant le "/")
                        categoriesArray.getString(idx).split("/").firstOrNull()?.trim() ?: ""
                    }.filter { it.isNotBlank() }.distinct()
                } else null

                val publisher = volumeInfo.optString("publisher").takeIf { it.isNotEmpty() }
                val publishedDate = volumeInfo.optString("publishedDate").takeIf { it.isNotEmpty() }
                val pageCount = if (volumeInfo.has("pageCount")) volumeInfo.optInt("pageCount") else null
                val language = volumeInfo.optString("language").takeIf { it.isNotEmpty() }
                val averageRating = if (volumeInfo.has("averageRating")) {
                    volumeInfo.optDouble("averageRating").toFloat()
                } else null

                results.add(
                    SearchResult(
                        title = title,
                        authors = authors,
                        isbn = isbn,
                        imageUrl = imageUrl,
                        description = description,
                        categories = categories,  // ✅ Liste de catégories
                        publisher = publisher,
                        publishedDate = publishedDate,
                        pageCount = pageCount,
                        language = language,
                        averageRating = averageRating
                    )
                )
            }
        } catch (e: Exception) {
            println("❌ Erreur parsing Google Books: ${e.message}")
        }

        return results
    }



}