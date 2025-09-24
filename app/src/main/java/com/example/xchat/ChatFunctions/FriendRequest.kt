package com.example.xchat.ChatFunctions

// FriendRequest.kt
data class FriendRequest(
    val requestId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val status: String = "",
    val senderName: String = "",
    val senderProfileImage: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
