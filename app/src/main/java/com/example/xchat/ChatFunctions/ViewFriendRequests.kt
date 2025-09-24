package com.example.xchat.ChatFunctions

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.xchat.R
import com.example.xchat.databinding.ActivityViewFriendRequestsBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ViewFriendRequests : AppCompatActivity() {
        private lateinit var binding: ActivityViewFriendRequestsBinding
        private lateinit var auth: FirebaseAuth
        private lateinit var firestore: FirebaseFirestore
        private lateinit var adapter: FriendRequestAdapter

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            binding = ActivityViewFriendRequestsBinding.inflate(layoutInflater)
            setContentView(binding.root)

            auth = FirebaseAuth.getInstance()
            firestore = FirebaseFirestore.getInstance()

            setupRecyclerView()
            loadFriendRequests()
        }

    private fun setupRecyclerView() {
        adapter = FriendRequestAdapter(emptyList()) { request, accept ->
            if (accept) {
                acceptFriendRequest(request)
            } else {
                rejectFriendRequest(request)
            }
        }
        binding.requestsRecyclerView.adapter = adapter
        binding.requestsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun loadFriendRequests() {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("friendRequests")
            .whereEqualTo("receiverId", currentUserId)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading requests", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                val requests = mutableListOf<FriendRequest>()
                val documents = snapshots?.documents ?: return@addSnapshotListener

                for (doc in documents) {
                    val request = doc.toObject(FriendRequest::class.java)?.copy(requestId = doc.id) ?: continue
                    firestore.collection("users").document(request.senderId).get()
                        .addOnSuccessListener { userDoc ->
                            val name = userDoc.getString("name") ?: "Unknown"
                            val image = userDoc.getString("profileImage") ?: ""
                            requests.add(request.copy(senderName = name, senderProfileImage = image))

                            if (requests.size == documents.size) {
                                adapter.updateList(requests)
                            }
                        }
                }
            }
    }

    private fun acceptFriendRequest(request: FriendRequest) {
        firestore.runTransaction { transaction ->
            // Update request status
            val requestRef = firestore.collection("friendRequests").document(request.requestId)
            transaction.update(requestRef, "status", "accepted")

            // Add to friends list with proper structure
            val currentUserFriendRef = firestore.collection("users")
                .document(request.receiverId)
                .collection("friends")
                .document(request.senderId)

            val otherUserFriendRef = firestore.collection("users")
                .document(request.senderId)
                .collection("friends")
                .document(request.receiverId)

            // Store minimal friend data
            val friendData = hashMapOf(
                "userId" to request.senderId,
                "timestamp" to System.currentTimeMillis()
            )

            transaction.set(currentUserFriendRef, friendData)
            transaction.set(otherUserFriendRef, hashMapOf(
                "userId" to request.receiverId,
                "timestamp" to System.currentTimeMillis()
            ))
        }.addOnSuccessListener {
            Toast.makeText(this, "Friend request accepted", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Error accepting request: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rejectFriendRequest(request: FriendRequest) {
        firestore.collection("friendRequests").document(request.requestId)
            .update("status", "rejected")
            .addOnSuccessListener {
                Toast.makeText(this, "Friend request rejected", Toast.LENGTH_SHORT).show()
            }
    }
}

