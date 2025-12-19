package com.mylibrary.model

data class BookRecommendation(
    val title: String,
    val author: String,
    val isbn: String?,
    val reason: String
)

data class RecommendationsResponse(
    val recommendations: List<BookRecommendation>,
    val cached: Boolean,
    val generatedAt: String
)

data class BookSummary(
    val summary: String,
    val isbn: String?,
    val cached: Boolean
)