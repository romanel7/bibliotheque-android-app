package com.mylibrary.model

data class Badge(
    val id: Int,
    val name: String,
    val minBooks: Int,
    val maxBooks: Int,
    val imageUrl: String,
    val isUnlocked: Boolean,
    val progress: Int,
    val booksNeeded: Int
)

data class BadgesResponse(
    val badges: List<Badge>,
    val totalBooksRead: Int
)