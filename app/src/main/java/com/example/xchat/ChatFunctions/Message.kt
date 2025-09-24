package com.example.xchat.ChatFunctions

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val imageBase64: String = "",
    val type: String = "text",
    val timestamp: Long = 0L,
    val status: Map<String, Any> = emptyMap()

)
