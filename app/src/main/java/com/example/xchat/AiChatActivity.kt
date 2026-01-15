package com.example.xchat

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.xchat.Adapters.AiChatAdapter
import com.example.xchat.Models.ChatMessage
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: AiChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var generativeModel: GenerativeModel

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)

        val apiKey = BuildConfig.GEMINI_API_KEY
        
        // Config with Safety Settings to prevent generic block errors
        val safetySettings = listOf(
            SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.ONLY_HIGH),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.ONLY_HIGH)
        )

        generativeModel = GenerativeModel(
            modelName = "gemini-flash-latest", 
            apiKey = apiKey,
            safetySettings = safetySettings,
            systemInstruction = content { text("You are XChat AI, a helpful assistant developed by Sudipta Roy. Provide helpful, concise answers.") }
        )

        recyclerView = findViewById(R.id.chatRecyclerView)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        progressBar = findViewById(R.id.progressBar)

        adapter = AiChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        recyclerView.adapter = adapter
        
        // Scroll to bottom when keyboard opens
        recyclerView.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
             if (bottom < oldBottom) {
                 recyclerView.postDelayed({
                     if (messages.isNotEmpty()) {
                         recyclerView.scrollToPosition(messages.size - 1)
                     }
                 }, 100)
             }
        }

        sendButton.setOnClickListener {
            sendMessage()
        }

        // DIAGNOSTIC CHECK
        checkAvailableModels(apiKey)

        // Load History
        loadChatHistory()
    }

    private fun loadChatHistory() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("ai_chat_history")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val message = document.toObject(ChatMessage::class.java)
                    messages.add(message)
                }
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
            .addOnFailureListener { exception ->
                Log.w("AiChatActivity", "Error getting history.", exception)
            }
    }

    private fun saveMessageToFirestore(message: ChatMessage) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("ai_chat_history")
            .add(message)
            .addOnFailureListener { e ->
                Log.w("AiChatActivity", "Error saving message", e)
            }
    }

    private fun checkAvailableModels(apiKey: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                    .build()
                
                val response = client.newCall(request).execute()
                val body = response.body?.string()
                
                Log.d("GEMINI_DIAG", "Models response code: ${response.code}")
                Log.d("GEMINI_DIAG", "Models response body: $body")

                withContext(Dispatchers.Main) {
                     if (!response.isSuccessful) {
                         messages.add(ChatMessage("System Error: Could not list models. Code ${response.code}. Body: $body", false))
                         adapter.notifyItemInserted(messages.size - 1)
                     } else {
                         // Only show success if needed, or just log it. 
                         // If 404 persists, the body here will tell us why (e.g. error details)
                         if (body != null && !body.contains("gemini-1.5-flash")) {
                              messages.add(ChatMessage("Ask anything", false))
                              adapter.notifyItemInserted(messages.size - 1)
                         }
                     }
                }

            } catch (e: Exception) {
                Log.e("GEMINI_DIAG", "Error listing models", e)
                 withContext(Dispatchers.Main) {
                     messages.add(ChatMessage("System Diagnostic Error: ${e.message}", false))
                     adapter.notifyItemInserted(messages.size - 1)
                 }
            }
        }
    }

    private fun sendMessage() {
        val userMessageText = messageInput.text.toString().trim()
        if (userMessageText.isEmpty()) return

        if (generativeModel.apiKey.isEmpty()) {
             Toast.makeText(this, "API Key is missing", Toast.LENGTH_LONG).show()
             return
        }

        val userMsg = ChatMessage(userMessageText, true)
        messages.add(userMsg)
        saveMessageToFirestore(userMsg)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.smoothScrollToPosition(messages.size - 1) // Smooth scroll is better for "sending"
        messageInput.text.clear()

        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = generativeModel.generateContent(userMessageText)
                val aiResponseText = response.text

                withContext(Dispatchers.Main) {
                    if (aiResponseText != null) {
                        val botMsg = ChatMessage(aiResponseText, false)
                        messages.add(botMsg)
                        saveMessageToFirestore(botMsg)
                        adapter.notifyItemInserted(messages.size - 1)
                        recyclerView.smoothScrollToPosition(messages.size - 1)
                    } else {
                        // If text is null, it might be blocked or empty
                         Toast.makeText(this@AiChatActivity, "Response was empty or blocked.", Toast.LENGTH_LONG).show()
                    }
                    progressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("AiChatActivity", "Error generating content", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AiChatActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    progressBar.visibility = View.GONE
                }
            }
        }
    }
}
