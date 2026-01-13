package com.example.xchat.Call_feature


import com.google.firebase.database.PropertyName
import com.google.gson.Gson
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

data class Call(
    val callId: String = "",
    val callerId: String = "",
    val receiverId: String = "",
    var callerName: String = "",
    val receiverName: String = "",
    @PropertyName("isVideo")  // ← Critical annotation
    val isVideo: Boolean = false,
    var status: String = "ringing",
    val timestamp: Long = System.currentTimeMillis()
)


data class SignalData(
    val type: String = "", // "offer", "answer", "candidate"
    val callId: String = "",
    val senderId: String = "",
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int? = null
)

data class CallNotification(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val isVideo: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)