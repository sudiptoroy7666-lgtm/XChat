package com.example.xchat.ChatFunctions


data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val profileImage: String = "",
    val status: String = "",
    var isFriend: Boolean = false
){
    // Add helper function for search
    fun matchesQuery(query: String): Boolean {
        return name.contains(query, true) // Case-insensitive check

    }
    }