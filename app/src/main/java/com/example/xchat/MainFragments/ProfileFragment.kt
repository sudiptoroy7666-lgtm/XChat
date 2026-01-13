package com.example.xchat.MainFragments

import com.example.xchat.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.xchat.Ui.AuthActivity
import com.example.xchat.databinding.FragmentProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream

class ProfileFragment : Fragment() {


    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var DeleteAcc: Button
    private lateinit var database: FirebaseDatabase
    private lateinit var firestore: FirebaseFirestore
    private var imageBase64: String? = null
    private var isEditMode = false
    private var originalUserData: Map<String, Any> = hashMapOf()


    private val realtimeDb = FirebaseDatabase.getInstance().reference


    companion object {
        private const val PICK_IMAGE_REQUEST = 100
        fun newInstance() = ProfileFragment()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return _binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize Firebase services
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        firestore = FirebaseFirestore.getInstance()
        DeleteAcc = view.findViewById(R.id.deleteAccButton)


        setupUI()
        loadUserData()
        setupClickListeners()

        DeleteAcc.setOnClickListener {
            showDeleteConfirmationDialog()
        }


        binding.logoutButton.setOnClickListener {
            logoutUser()
        }




    }
    private fun logoutUser() {
        // Firebase logout
        FirebaseAuth.getInstance().signOut()

        // Clear saved login state
        val sharedPrefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().clear().apply()

        // Navigate to AuthActivity
        val intent = Intent(requireActivity(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone!")
            .setPositiveButton("Delete") { _, _ -> deleteUserAccount() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteUserAccount() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Prompt for password before reauthentication
        val input = EditText(requireContext())
        input.hint = "Enter password to confirm"
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD

        AlertDialog.Builder(requireContext())
            .setTitle("Re-authenticate")
            .setMessage("Please enter your password to confirm account deletion.")
            .setView(input)
            .setPositiveButton("Confirm") { _, _ ->
                val password = input.text.toString().trim()
                if (password.isEmpty()) {
                    Toast.makeText(context, "Password cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val email = user.email ?: return@setPositiveButton

                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        proceedWithDeletion(user)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private fun proceedWithDeletion(user: FirebaseUser) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                user.delete().await()
                deleteFirestoreData(user.uid)
                deleteRealtimeData(user.uid)

                // Clear shared preferences
                val sharedPrefs = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                sharedPrefs.edit().clear().apply()

                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Account deleted successfully", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), AuthActivity::class.java))
                    requireActivity().finish()
                }
            } catch (e: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Deletion failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private suspend fun deleteFirestoreData(userId: String) {
        val batch = firestore.batch()

        // Delete main user document
        val userRef = firestore.collection("users").document(userId)
        batch.delete(userRef)

        // Delete call history
        firestore.collection("users").document(userId)
            .collection("callHistory").get().await().forEach {
                batch.delete(it.reference)
            }

        // Remove user from all chats
        firestore.collection("chats")
            .whereArrayContains("members", userId)
            .get().await().forEach { chatDoc ->
                val members = chatDoc.get("members") as List<String>
                val newMembers = members.filter { it != userId }

                if (newMembers.isEmpty()) {
                    batch.delete(chatDoc.reference)
                } else {
                    batch.update(chatDoc.reference, "members", newMembers)
                }
            }

        // Update group chats
        firestore.collection("groupChats")
            .whereArrayContains("members", userId)
            .get().await().forEach { groupDoc ->
                val members = groupDoc.get("members") as List<String>
                val newMembers = members.filter { it != userId }
                val updates = mutableMapOf<String, Any>(
                    "members" to newMembers
                )

                if (groupDoc.getString("admin") == userId && newMembers.isNotEmpty()) {
                    updates["admin"] = newMembers.first()
                }

                batch.update(groupDoc.reference, updates)
            }

        // Delete notifications
        firestore.collection("notifications").document(userId)
            .delete().await()

        // Delete call records
        firestore.collection("calls")
            .whereIn("callerId", listOf(userId))
            .get().await().forEach { batch.delete(it.reference) }

        firestore.collection("calls")
            .whereIn("receiverId", listOf(userId))
            .get().await().forEach { batch.delete(it.reference) }

        batch.commit().await()
    }

    private suspend fun deleteRealtimeData(userId: String) {
        // Delete user status
        realtimeDb.child("userStatus").child(userId).removeValue().await()

        // Remove from chat status
        realtimeDb.child("chatStatus").get().await().children.forEach { chatSnapshot ->
            chatSnapshot.children.forEach { statusSnapshot ->
                when (statusSnapshot.key) {
                    "typing" -> {
                        val typingStatus = statusSnapshot.value as? Map<String, Boolean> ?: return@forEach
                        val updatedStatus = typingStatus.filterKeys { it != userId }
                        realtimeDb.child("chatStatus/${chatSnapshot.key}/typing").setValue(updatedStatus)
                    }
                    "lastMessageRead" -> {
                        val readStatus = statusSnapshot.value as? Map<String, String> ?: return@forEach
                        val updatedRead = readStatus.filterKeys { it != userId }
                        realtimeDb.child("chatStatus/${chatSnapshot.key}/lastMessageRead").setValue(updatedRead)
                    }
                }
            }
        }

        // Remove from ongoing calls
        realtimeDb.child("ongoingCalls").get().await().children.forEach { callSnapshot ->
            if (callSnapshot.child("callerId").getValue(String::class.java) == userId ||
                callSnapshot.child("receiverId").getValue(String::class.java) == userId) {
                callSnapshot.ref.removeValue()
            }
        }
    }













    private fun setupUI() {
        // Initially set to view mode
        setEditMode(false)
    }

    private fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
        if (editMode) {
            binding.nameEditText.isEnabled = true
            binding.statusEditText.isEnabled = true
          //  binding.emailNotificationsSwitch.isEnabled = true
            binding.editButton.text = getString(R.string.save)
            binding.cancelButton.visibility = View.VISIBLE
        } else {
            binding.nameEditText.isEnabled = false
            binding.statusEditText.isEnabled = false
          //  binding.emailNotificationsSwitch.isEnabled = false
            binding.editButton.text = getString(R.string.edit)
            binding.cancelButton.visibility = View.GONE
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        showLoading(true)

        // Load from Firestore first
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    originalUserData = document.data ?: hashMapOf()
                    document.data?.let { displayUserData(it) }
                    showLoading(false)
                } else {
                    // Fallback to Realtime Database if Firestore doesn't have data
                    loadFromRealtimeDatabase(userId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ViewProfile", "Error loading from Firestore", e)
                loadFromRealtimeDatabase(userId)
            }


    }

    private fun loadFromRealtimeDatabase(userId: String) {
        database.reference.child("users").child(userId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val userData = snapshot.value as? Map<String, Any> ?: hashMapOf()
                        originalUserData = userData
                        displayUserData(userData)
                    } else {
                        Toast.makeText(context, "No profile data found", Toast.LENGTH_SHORT).show()
                    }
                    showLoading(false)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("ViewProfile", "Error loading from Realtime DB", error.toException())
                    showLoading(false)
                    Toast.makeText(context, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            })
    }


    private fun displayUserData(userData: Map<String, Any>) {
        val safeBinding = _binding ?: return  // Exit if view destroyed

        safeBinding.nameEditText.setText(userData["name"] as? String ?: "")
        safeBinding.statusEditText.setText(userData["status"] as? String ?: "Hey there!")
        safeBinding.emailEditText.setText(userData["email"] as? String ?: "")
        safeBinding.emailEditText.isEnabled = false
// FIX: Get email from database FIRST, then fallback to Firebase Auth
        val emailFromDb = userData["email"] as? String
        val currentUserEmail = auth.currentUser?.email

        // Prefer database email if exists, otherwise use Firebase Auth email
        val emailToShow = emailFromDb ?: currentUserEmail ?: ""
        safeBinding.emailEditText.setText(emailToShow)
        safeBinding.emailEditText.isEnabled = false  // Keep disabled as intended
        val emailNotifications = userData["emailNotifications"] as? Boolean ?: true
     //   binding.emailNotificationsSwitch.isChecked = emailNotifications

        val callStatus = userData["callStatus"] as? String ?: "available"
       // binding.callStatusText.text = "Call status: $callStatus"

        // Load profile image if available
        val profileImage = userData["profileImage"] as? String
        if (!profileImage.isNullOrEmpty()) {
            if (profileImage.startsWith("http")) {
                // Load from URL using your preferred image loading library (Glide/Picasso)
                // Example with Glide:
                // Glide.with(this).load(profileImage).into(binding.profileImage)
            } else {
                // Assume it's Base64 encoded
                try {
                    val imageBytes = Base64.decode(profileImage, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    binding.profileImage.setImageBitmap(bitmap)
                } catch (e: Exception) {
                    Log.e("ViewProfile", "Error decoding profile image", e)
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.profileImage.setOnClickListener {
            if (isEditMode) {
                pickImageFromGallery()
            }
        }

        binding.editButton.setOnClickListener {
            if (isEditMode) {
                saveProfileData()
            } else {
                setEditMode(true)
            }
        }

        binding.cancelButton.setOnClickListener {
            setEditMode(false)
            // Restore original data
            displayUserData(originalUserData)
        }

      //  binding.callStatusButton.setOnClickListener {
       //     // Implement call status change logic if needed
       //     Toast.makeText(context, "Changing call status...", Toast.LENGTH_SHORT).show()
     //   }
   }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            data.data?.let { uri ->
                binding.profileImage.setImageURI(uri)
                imageBase64 = convertImageToBase64(uri)
            }
        }
    }

    private fun convertImageToBase64(uri: Uri): String {
        return try {
            val inputStream: InputStream? = context?.contentResolver?.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()

            // Compress image to reduce size
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream)

            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
            ""
        }
    }

    private fun saveProfileData() {
        val name = binding.nameEditText.text.toString().trim()
        val status = binding.statusEditText.text.toString().trim()
      //  val emailNotifications = binding.emailNotificationsSwitch.isChecked
        val userId = auth.currentUser?.uid ?: return

        if (name.isEmpty()) {
            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        binding.editButton.isEnabled = false

        val updatedData = hashMapOf<String, Any>(
            "name" to name,
            "status" to status,
          //  "emailNotifications" to emailNotifications,
            "lastSeen" to System.currentTimeMillis()
        )

        // Add profile image if it was changed
        imageBase64?.let {
            updatedData["profileImage"] = it
        }

        // Update Firestore
        firestore.collection("users").document(userId)
            .update(updatedData)
            .addOnSuccessListener {
                // Update Realtime Database
                database.reference.child("users").child(userId)
                    .updateChildren(updatedData)
                    .addOnSuccessListener {
                        // Also update lastSeen in userStatus
                        database.reference.child("userStatus").child(userId)
                            .child("lastSeen").setValue(System.currentTimeMillis().toString())

                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                        setEditMode(false)
                        showLoading(false)
                        binding.editButton.isEnabled = true
                    }
                    .addOnFailureListener { e ->
                        Log.e("ViewProfile", "Failed to update Realtime DB", e)
                        Toast.makeText(context, "Failed to update profile in Realtime DB", Toast.LENGTH_SHORT).show()
                        showLoading(false)
                        binding.editButton.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ViewProfile", "Failed to update Firestore", e)
                Toast.makeText(context, "Failed to update profile", Toast.LENGTH_SHORT).show()
                showLoading(false)
                binding.editButton.isEnabled = true
            }
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.contentLayout.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.contentLayout.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



}




