package com.example.xchat.MainFragments

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat.finishAffinity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.xchat.ChatFunctions.ChatActivity
import com.example.xchat.ChatFunctions.User
import com.example.xchat.ChatFunctions.UserAdapter
import com.example.xchat.ChatFunctions.ViewFriendRequests
import com.example.xchat.ProfileActivity
import com.example.xchat.databinding.FragmentChatBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: UserAdapter
    private var friendsListener: ListenerRegistration? = null
    private val searchResults = mutableListOf<User>()
    // Search-related variables
    private val friendsList = mutableListOf<User>()
    private val allUsers = mutableListOf<User>()
    private var allUsersLoaded = false


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        setupViews()
        setupRecyclerView()
        setupSearchView()
        loadFriends()
        loadAllUsers() // Load all users for search
    }

    private fun setupViews() {
        binding.frButton.setOnClickListener {
            startActivity(Intent(activity, ViewFriendRequests::class.java))
        }
        binding.aiChatButton.setOnClickListener {
            startActivity(Intent(activity, com.example.xchat.AiChatActivity::class.java))
        }
        binding.gotoaichat.setOnClickListener {
            startActivity(Intent(activity, com.example.xchat.AiChatActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        adapter = UserAdapter(emptyList()) { user ->
            checkFriendshipStatus(user)
        }
        binding.usersRecyclerView.apply {
            adapter = this@ChatFragment.adapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(true)
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { searchUsers(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText.isNullOrEmpty()) {
                    isSearching = false
                    showFriends()
                } else {
                    isSearching = true
                    searchUsers(newText)
                }
                return true
            }
        })
    }

    private fun loadAllUsers() {
        _binding?.progressBar?.visibility = View.VISIBLE

        firestore.collection("users").get()
            .addOnSuccessListener { result ->
                allUsers.clear()
                allUsers.addAll(result.documents.map { doc ->
                    User(
                        id = doc.id,
                        name = doc.getString("name") ?: "Unknown User",
                        email = doc.getString("email") ?: "",
                        profileImage = doc.getString("profileImage") ?: "",
                        status = doc.getString("status") ?: "",
                        isFriend = friendsList.any { it.id == doc.id }
                    )
                })
                Log.d("SEARCH", "Loaded ${allUsers.size} users")
                allUsersLoaded = true
                _binding?.progressBar?.visibility = View.GONE


                // Update search if query exists
                val query = binding.searchView.query.toString()
                if (query.isNotEmpty()) searchUsers(query)
            }
            .addOnFailureListener { e ->
                Log.e("SEARCH", "Error loading users", e)
                _binding?.progressBar?.visibility = View.GONE
                Toast.makeText(requireContext(), "Error loading users", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadFriends() {
        _binding?.let { binding ->
            adapter.updateList(friendsList)
            binding.emptyState.visibility = if (friendsList.isEmpty()) View.VISIBLE else View.GONE
        }
        val currentUserId = auth.currentUser?.uid ?: return

        friendsListener = firestore.collection("users").document(currentUserId)
            .collection("friends")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.e("ChatFragment", "Friends load error", error)
                    return@addSnapshotListener
                }

                val friendIds = snapshots?.documents?.mapNotNull { it.id } ?: emptyList()
                if (friendIds.isEmpty()) {
                    showEmptyState()
                    return@addSnapshotListener
                }

                fetchFriendDetails(friendIds)
            }
    }

    private fun checkFriendshipStatus(user: User) {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(currentUserId)
            .collection("friends").document(user.id)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // Friend - go to chat
                    val intent = (Intent(requireContext(), ChatActivity::class.java).apply {
                        putExtra("userId", user.id)
                        startActivity(this)


                    })
                } else {
                    // Not friend - go to profile with request option
                    val intent = Intent(requireContext(), ProfileActivity::class.java).apply {
                        putExtra("userId", user.id)
                        putExtra("showRequestButton", true)
                        startActivity(this)

                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Error checking friendship", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchFriendDetails(friendIds: List<String>) {
        firestore.collection("users")
            .whereIn(FieldPath.documentId(), friendIds)
            .get()
            .addOnSuccessListener { result ->
                friendsList.clear()
                friendsList.addAll(result.documents.map { doc ->
                    User(
                        id = doc.id,
                        name = doc.getString("name") ?: "",
                        email = doc.getString("email") ?: "",
                        profileImage = doc.getString("profileImage") ?: "",
                        status = doc.getString("status") ?: "",
                        isFriend = true
                    )
                })

                // Update friend status in allUsers list
                allUsers.forEach { user ->
                    user.isFriend = friendsList.any { it.id == user.id }
                }

                if (!isSearching) showFriends()
                else searchUsers(binding.searchView.query.toString())
            }
    }

    private fun searchUsers(query: String) {
        if (!allUsersLoaded) {
            Toast.makeText(requireContext(), "Still loading users...", Toast.LENGTH_SHORT).show()
            return
        }

        val cleanQuery = query.trim().lowercase(Locale.getDefault())
        if (cleanQuery.isEmpty()) {
            showFriends()
            return
        }

        val filtered = allUsers.filter {
            it.name.lowercase(Locale.getDefault()).contains(cleanQuery) &&
                    it.id != auth.currentUser?.uid
        }

        Log.d("SEARCH", "Displaying ${filtered.size} results for '$cleanQuery'")
        if (filtered.isNotEmpty()) {
            binding.emptyState.visibility = View.GONE
            binding.usersRecyclerView.visibility = View.VISIBLE
            adapter.updateList(filtered)
        } else {
            binding.emptyState.visibility = View.VISIBLE
            binding.usersRecyclerView.visibility = View.GONE
        }
    }

    private fun showFriends() {
        adapter.updateList(friendsList)
        binding.emptyState.visibility = if (friendsList.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showEmptyState() {
        binding.emptyState.visibility = View.VISIBLE
        binding.usersRecyclerView.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        friendsListener?.remove()
        _binding = null
    }

    // Handle configuration changes
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("SEARCHING", isSearching)
    }

    companion object {
        private var isSearching = false
    }
}