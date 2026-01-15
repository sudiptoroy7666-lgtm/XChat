package com.example.xchat

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.bumptech.glide.Glide
import com.example.xchat.ChatFunctions.FriendRequest
import com.example.xchat.Ui.FriendProfileFragment
import com.example.xchat.databinding.ActivityProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private var currentUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        currentUserId = auth.currentUser?.uid
        userId = intent.getStringExtra("userId")

        checkFriendStatus()
        loadUserProfile()

        setupButton()
    }

    private fun loadUserProfile() {
        userId?.let { uid ->
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    document?.let {
                        binding.userName.text = it.getString("name")
                        binding.userStatus.text = it.getString("status")

                        val profileImage = it.getString("profileImage") ?: ""

                        if (profileImage.startsWith("http")) {
                            // Load image from URL using Glide
                            Glide.with(this)
                                .load(profileImage)
                                .placeholder(R.drawable.profilepicture)
                                .into(binding.profileImage)
                        } else if (profileImage.isNotEmpty()) {
                            // Decode base64 image string
                            try {
                                val imageBytes = android.util.Base64.decode(profileImage, android.util.Base64.DEFAULT)
                                val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                binding.profileImage.setImageBitmap(bitmap)
                            } catch (e: Exception) {
                                binding.profileImage.setImageResource(R.drawable.profilepicture)
                            }
                        } else {
                            binding.profileImage.setImageResource(R.drawable.profilepicture)
                        }
                    }
                }
        }
    }


    private fun checkFriendStatus() {
        if (userId == currentUserId) {
            binding.friendRequestButton.visibility = View.GONE
            return
        }

        currentUserId?.let { currentUser ->
            userId?.let { targetUser ->
                firestore.collection("friendRequests")
                    .whereEqualTo("senderId", currentUser)
                    .whereEqualTo("receiverId", targetUser)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (documents.isEmpty) {
                            binding.friendRequestButton.text = "Send Friend Request"
                        } else {
                            binding.friendRequestButton.text = "Request Sent"
                            binding.friendRequestButton.isEnabled = false
                            showFriendProfileFragment()
                            binding.friendRequestButton.visibility = View.GONE
                        }
                    }
            }
        }
    }


    private fun showFriendProfileFragment() {
        // Hide parent activity views to avoid duplication
        binding.profileImage.visibility = View.GONE
        binding.userName.visibility = View.GONE
        binding.userStatus.visibility = View.GONE
        
        val bundle = Bundle().apply {
            putString("userId", userId)
        }

        val fragment = FriendProfileFragment().apply {
            arguments = bundle
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_containerOfProfile, fragment)
            .commit()
    }



    private fun setupButton() {
        binding.friendRequestButton.setOnClickListener {
            sendFriendRequest()
        }
    }

    private fun sendFriendRequest() {
        // Validate both IDs exist
        val senderId = currentUserId ?: return
        val receiverId = userId ?: return

        // First check if receiver exists
        firestore.collection("users").document(receiverId).get()
            .addOnSuccessListener OnSuccessListener@{ receiverDoc ->
                if (!receiverDoc.exists()) {
                    Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
                    return@OnSuccessListener
                }

                // Check for existing requests
                firestore.collection("friendRequests")
                    .whereEqualTo("senderId", senderId)
                    .whereEqualTo("receiverId", receiverId)
                    .whereIn("status", listOf("pending", "accepted"))
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        if (querySnapshot.isEmpty) {
                            // Create new request
                            val request = FriendRequest(
                                senderId = senderId,
                                receiverId = receiverId,
                                status = "pending"
                            )

                            firestore.collection("friendRequests").add(request)
                                .addOnSuccessListener {
                                    updateUIRequestSent()
                                    Toast.makeText(this, "Request sent!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            Toast.makeText(this, "Request already exists", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking user: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUIRequestSent() {
        binding.friendRequestButton.apply {
            text = "Request Sent"
            isEnabled = false
        }
    }
}