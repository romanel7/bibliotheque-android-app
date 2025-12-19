package com.mylibrary.model

import com.google.gson.annotations.SerializedName

// ==========================================
// Modèle pour les livres
// ==========================================
data class Book(
    val id: Int? = null,
    val titre: String,
    val auteur: String,
    val categories: List<String>? = null,
    val isbn: String?,
    val publisher: String?,

    @SerializedName("published_date")
    val publishedDate: String?,

    @SerializedName("page_count")
    val pageCount: Int?,

    val language: String?,

    @SerializedName("average_rating_google")
    val averageRatingGoogle: Float?,

    val note: Int? = null,
    val status: String = "à lire",
    val memo: String?,

    @SerializedName("image_url")
    val imageUrl: String?,

    @SerializedName("date_lecture")
    val dateLecture: String?,

    @SerializedName("date_ajout")
    val dateAjout: String? = null,

    @SerializedName("date_modification")
    val dateModification: String? = null,

    @SerializedName("user_id")
    val userId: Int? = null
)

// ==========================================
// Modèles pour Google Books API
// ==========================================
data class GoogleBooksResponse(
    val items: List<GoogleBookItem>?
)

data class GoogleBookItem(
    val volumeInfo: GoogleVolumeInfo
)

data class GoogleVolumeInfo(
    val title: String?,
    val authors: List<String>?,
    val description: String?,
    val publisher: String?,
    val publishedDate: String?,
    val pageCount: Int?,
    val language: String?,
    val averageRating: Float?,
    val categories: List<String>?,  // ✅ Déjà bon !
    val imageLinks: GoogleImageLinks?,
    val industryIdentifiers: List<GoogleIndustryIdentifier>?
)

data class GoogleImageLinks(
    val thumbnail: String?,
    val smallThumbnail: String?
)

data class GoogleIndustryIdentifier(
    val type: String,
    val identifier: String
)

// ==========================================
// Modèle simplifié pour la recherche
// ==========================================
data class SearchResult(
    val title: String,
    val authors: String,
    val isbn: String?,
    val imageUrl: String?,
    val description: String?,
    val categories: List<String>? = null,
    val publisher: String? = null,
    val publishedDate: String? = null,
    val pageCount: Int? = null,
    val language: String? = null,
    val averageRating: Float? = null
)