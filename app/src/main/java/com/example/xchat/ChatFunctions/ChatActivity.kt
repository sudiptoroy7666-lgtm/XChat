package com.example.xchat.ChatFunctions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity

import com.example.xchat.R
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xchat.Call_feature.Call
import com.example.xchat.Call_feature.OutgoingCallActivity
import com.example.xchat.MainFragments.ProfileFragment
import com.example.xchat.ProfileActivity
import com.example.xchat.databinding.ActivityChatBinding

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.ByteArrayOutputStream
import java.io.InputStream
import android.app.NotificationManager
import com.example.xchat.Call_feature.IncomingCallActivity
import com.google.firebase.database.ChildEventListener


class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase
    private lateinit var adapter: MessageAdapter
    private var chatId: String = ""
    private lateinit var receiverId: String
    private var imageBase64: String? = null

    private val CALL_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val REQUEST_CALL_PERMISSIONS = 1001
    private lateinit var callReference: DatabaseReference
    private val pickImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                imageBase64 = convertImageToBase64(uri)
                imageBase64?.let { sendMessage(image = it) }
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

// Get receiver details
        receiverId = intent.getStringExtra("userId") ?: return finish()

        // Set up toolbar
        setupToolbar()



        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()
        receiverId = intent.getStringExtra("userId") ?: return finish()
        val currentUserId = auth.currentUser?.uid ?: return finish()

        // Generate unique chat ID
        chatId = listOf(currentUserId, receiverId).sorted().joinToString("_")

        setupViews()
        setupRecyclerView()
        loadMessages()
        setupTypingListener()
        fetchReceiverProfile()

        binding.receiverProfileImage.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", receiverId)
            startActivity(intent)
        }

        binding.nameOfCUser.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("userId", receiverId)
            startActivity(intent)
        }



        callReference = FirebaseDatabase.getInstance().getReference("ongoingCalls")

        // Setup call button
        binding.callButton.setOnClickListener {
            checkCallPermissions(false) // false for audio call
        }

        // Optionally add a video call button
      //  binding.videoCallButton.setOnClickListener {
      //      checkCallPermissions(true) // true for video call
      //  }


    }

    private fun setupViews() {
        binding.sendButton.setOnClickListener {
            if (imageBase64 != null) {
                sendMessage(image = imageBase64!!)
            } else {
                sendMessage()
            }
        }

        binding.attachButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImage.launch(intent)
        }

        binding.messageInput.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                sendMessage()
                true
            } else {
                updateTypingStatus(true)
                false
            }
        }

        binding.messageInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                updateTypingStatus(s?.isNotEmpty() == true)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }





    private fun setupToolbar() {
        with(binding.chatToolbar) {
            setSupportActionBar(this)
            supportActionBar?.setDisplayShowTitleEnabled(false)

            binding.btnBack.setOnClickListener {
                finish()
            }
        }
    }



    private fun fetchReceiverProfile() {
        firestore.collection("users").document(receiverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profileImage = document.getString("profileImage") ?: ""
                    val userName = document.getString("name") ?: "Unknown"

                    // Set name in toolbar
                    binding.nameOfCUser.text = userName

                    // Load profile image
                    if (profileImage.startsWith("http")) {
                        Glide.with(this)
                            .load(profileImage)
                            .placeholder(R.drawable.profilepicture)
                            .into(binding.receiverProfileImage)
                    } else if (profileImage.isNotEmpty()) { // Handle Base64 encoded image
                        try {
                            val imageBytes = Base64.decode(profileImage, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.receiverProfileImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.receiverProfileImage.setImageResource(R.drawable.profilepicture)
                        }
                    } else {
                        binding.receiverProfileImage.setImageResource(R.drawable.profilepicture)
                    }
                }
            }
            .addOnFailureListener {
                binding.receiverProfileImage.setImageResource(R.drawable.profilepicture)
                binding.nameOfCUser.text = "Unknown"
            }
    }


    private fun setupRecyclerView() {
        adapter = MessageAdapter(auth.currentUser?.uid ?: "")
        val linearLayoutManager = LinearLayoutManager(this@ChatActivity).apply {
            stackFromEnd = true
        }
        binding.messagesRecyclerView.apply {
            layoutManager = linearLayoutManager
            adapter = this@ChatActivity.adapter
            
            // Scroll to bottom when keyboard opens (layout resizes)
            addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
                if (bottom < oldBottom) {
                    postDelayed({
                        if (adapter != null && adapter!!.itemCount > 0) {
                            scrollToPosition(adapter!!.itemCount - 1)
                        }
                    }, 100)
                }
            }
        }
        
        // Auto-scroll when new messages are added
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                binding.messagesRecyclerView.post {
                     if (adapter.itemCount > 0) {
                        binding.messagesRecyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                     }
                }
            }
        })
    }

    private fun loadMessages() {
        firestore.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading messages", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val messages = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Message::class.java)?.copy(id = doc.id)
                } ?: emptyList()

                adapter.submitList(messages)
            }
    }
    private fun convertImageToBase64(uri: Uri): String {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            ""
        }
    }

    private fun sendMessage(text: String = "", image: String = "") {
        val messageText = binding.messageInput.text.toString().trim()
        if (text.isEmpty() && image.isEmpty() && messageText.isEmpty()) return

        val currentUserId = auth.currentUser?.uid ?: return
        val chatDocRef = firestore.collection("chats").document(chatId)

        val message = if (image.isNotEmpty()) {
            Message(
                senderId = currentUserId,
                receiverId = receiverId,
                imageBase64 = image,
                type = "image",
                timestamp = System.currentTimeMillis()
            )
        } else {
            Message(
                senderId = currentUserId,
                receiverId = receiverId,
                text = messageText,
                type = "text",
                timestamp = System.currentTimeMillis()
            )
        }

        // Clear input after sending
        binding.messageInput.text.clear()
        imageBase64 = null

        chatDocRef.collection("messages").add(message)
            .addOnSuccessListener {
                // Use set() with merge to create the document if it doesn't exist
                val updates = mapOf(
                    "lastMessage" to (if (image.isNotEmpty()) "📷 Photo" else messageText),
                    "lastMessageTime" to System.currentTimeMillis()
                )
                chatDocRef.set(updates, SetOptions.merge())



                .addOnFailureListener { e ->
                    Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Chat setup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun setupTypingListener() {
        database.reference.child("chatStatus").child(chatId).child("typing")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val typingStatus = snapshot.getValue(object : GenericTypeIndicator<Map<String, Boolean>>() {})
                        ?: return

                    val isTyping = typingStatus[receiverId] ?: false
                    binding.typingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun updateTypingStatus(isTyping: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        database.reference.child("chatStatus").child(chatId).child("typing")
            .child(currentUserId).setValue(isTyping)
    }


    private fun checkCallPermissions(isVideo: Boolean) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Request permissions
            ActivityCompat.requestPermissions(
                this,
                CALL_PERMISSIONS,
                if (isVideo) REQUEST_CALL_PERMISSIONS + 1 else REQUEST_CALL_PERMISSIONS
            )
        } else {
            startCall(isVideo)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CALL_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startCall(false) // Audio call
                }
            }
            REQUEST_CALL_PERMISSIONS + 1 -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    startCall(true) // Video call
                }
            }
        }
    }

    private fun startCall(isVideo: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Check if user is already in a call
        FirebaseDatabase.getInstance().getReference("userStatus")
            .child(currentUserId)
            .child("callStatus")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val callStatus = snapshot.getValue(String::class.java) ?: "available"

                    if (callStatus != "available") {
                        Toast.makeText(
                            this@ChatActivity,
                            "You are already in a call",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    // Get receiver's call status
                    FirebaseDatabase.getInstance().getReference("userStatus")
                        .child(receiverId)
                        .child("callStatus")
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(receiverSnapshot: DataSnapshot) {
                                val receiverCallStatus = receiverSnapshot.getValue(String::class.java) ?: "available"

                                if (receiverCallStatus != "available") {
                                    Toast.makeText(
                                        this@ChatActivity,
                                        "User is busy on another call",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return
                                }

                                // All checks passed, start the call
                                proceedWithCall(isVideo)
                            }

                            override fun onCancelled(error: DatabaseError) {}
                        })
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun proceedWithCall(isVideo: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return
        val callId = callReference.push().key ?: return

        // Get current user's name for display
        firestore.collection("users").document(currentUserId)
            .get()
            .addOnSuccessListener { currentUserDoc ->
                val currentUser = auth.currentUser ?: return@addOnSuccessListener
                val userName = currentUserDoc.getString("name") ?: currentUser.displayName ?: currentUser.email ?: "You"

                // Get receiver's name
                firestore.collection("users").document(receiverId)
                    .get()
                    .addOnSuccessListener { receiverDoc ->
                        val receiverName = receiverDoc.getString("name") ?: "Unknown"

                        // Create call object
                        val call = Call(
                            callId = callId,
                            callerId = currentUserId,
                            receiverId = receiverId,
                            callerName = userName,
                            receiverName = receiverName,
                            isVideo = isVideo,  // ← This will save as "isVideo" in Firebase
                            status = "ringing"
                        )

                        // Save call to database
                        callReference.child(callId).setValue(call)
                            .addOnSuccessListener {
                                // Start outgoing call activity
                                startActivity(
                                    OutgoingCallActivity.newIntent(
                                    this,
                                    callId,
                                    receiverId,
                                    receiverName,
                                    isVideo
                                ))
                            }
                    }
            }
    }


    // Add to your existing onPause() method
    override fun onPause() {
        super.onPause()
        updateTypingStatus(false)
    }
}
