package com.example.xchat.pushNotifiAndCall

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.xchat.ChatFunctions.Message
import com.example.xchat.R
import com.example.xchat.pushNotifiAndCall.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import java.net.URISyntaxException
import kotlin.concurrent.thread


class SocketService : Service() {

    private lateinit var socket: Socket
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var currentUserId: String
    private val firestore = FirebaseFirestore.getInstance()
    private val channelId = "socket_notification_channel"
    private val notificationId = 101

    override fun onCreate() {
        super.onCreate()

        // Setup helpers
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        notificationHelper = NotificationHelper(this)

        // Create notification channel
        createNotificationChannel()

        // Start listening
        initializeSocket()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("XChat Running")
            .setSmallIcon(R.drawable.app_icon) // Add this icon
            .build()

        // Start as foreground service
        startForeground(notificationId, notification)

        return START_STICKY // Restart if killed by system
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Handles incoming chat messages and notifications"
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun initializeSocket() {
        try {
            socket = IO.socket("http://192.168.1.10:3000") // Replace with your real server URL

            socket.on(Socket.EVENT_CONNECT) {
                Log.d("SocketService", "Connected to socket server")
                socket.emit("registerUser", currentUserId)
            }

            socket.on(Socket.EVENT_DISCONNECT) { reason ->
                Log.e("SocketService", "Disconnected: $reason. Reconnecting...")
                thread {
                    Thread.sleep(5000)
                    socket.connect()
                }
            }

            socket.on("newMessage") { args ->
                try {
                    val messageJson = args[0] as JSONObject
                    val senderId = messageJson.getString("senderId")
                    val receiverId = messageJson.getString("receiverId")

                    if (receiverId != currentUserId) return@on

                    val message = Message(
                        id = messageJson.getString("id"),
                        senderId = senderId,
                        receiverId = receiverId,
                        text = messageJson.optString("text", ""),
                        type = messageJson.optString("type", "text"),
                        timestamp = messageJson.getLong("timestamp"),
                        status = mapOf("delivered" to "true"),
                        imageBase64 = messageJson.optString("imageBase64", "")
                    )

                    markMessageAsDelivered(message.id, senderId)

                    if (!isAppInForeground()) {
                        val senderName = if (messageJson.has("senderName"))
                            messageJson.getString("senderName")
                        else
                            fetchSenderNameSync(senderId)

                        notificationHelper.showMessageNotification(message, senderName)
                    }
                } catch (e: Exception) {
                    Log.e("SocketService", "Error parsing newMessage: ${e.message}")
                }
            }

            socket.on("messageStatus") { args ->
                try {
                    val statusJson = args[0] as JSONObject
                    val messageId = statusJson.getString("messageId")
                    val statusObject = statusJson.getJSONObject("status")

                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@on
                    val otherUserId = getOtherUserId(statusObject)
                    val chatId = getChatId(userId, otherUserId)

                    firestore.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .document(messageId)
                        .update("status", jsonObjectToMap(statusObject))
                } catch (e: Exception) {
                    Log.e("SocketService", "Error updating message status: ${e.message}")
                }
            }

            socket.connect()

        } catch (e: URISyntaxException) {
            Log.e("SocketService", "Invalid socket URL", e)
        }
    }

    private fun getOtherUserId(statusObject: JSONObject): String {
        val keys = statusObject.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val key = rawKey as? String ?: continue
            if (key != currentUserId) {
                return key
            }
        }
        return ""
    }

    private fun getChatId(userId1: String, userId2: String): String {
        return listOf(userId1, userId2).sorted().joinToString("_")
    }

    private fun markMessageAsDelivered(messageId: String, senderId: String) {
        socket.emit("messageRead", JSONObject().apply {
            put("messageId", messageId)
            put("senderId", senderId)
            put("userId", currentUserId)
        })
    }

    private fun fetchSenderNameSync(senderId: String): String {
        return try {
            val task = firestore.collection("users").document(senderId).get()
            val document = task.result
            document?.getString("name") ?: "New message"
        } catch (e: Exception) {
            "New message"
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        for (processInfo in runningProcesses) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND && processInfo.processName == packageName) {
                return true
            }
        }
        return false
    }

    private fun jsonObjectToMap(jsonObject: JSONObject): Map<String, Any> {
        val map = mutableMapOf<String, Any>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val rawKey = keys.next()
            val key = rawKey as? String ?: continue
            val value = when (val rawValue = jsonObject.get(rawKey)) {
                is String -> rawValue
                null -> ""
                else -> rawValue.toString()
            }
            map[key] = value
        }
        return map
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        socket.disconnect()
        socket.off()
        Toast.makeText(this, "SocketService destroyed", Toast.LENGTH_SHORT).show()
        super.onDestroy()
    }
}