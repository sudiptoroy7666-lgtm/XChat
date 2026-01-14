package com.example.xchat.Models

data class ChatMessage(
    val text: String = "",
    val fromUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
