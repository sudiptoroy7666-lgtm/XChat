package com.example.xchat.Models

data class CallLog(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val userProfileImage: String = "",
    val callType: String = "", // "incoming", "outgoing", "missed"
    val isVideo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0 // Duration in seconds, 0 for missed calls
)
