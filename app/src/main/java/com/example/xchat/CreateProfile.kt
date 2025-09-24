package com.example.xchat

import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.xchat.Ui.AuthActivity
import com.example.xchat.databinding.ActivityCreateProfileBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale


 class CreateProfile : AppCompatActivity() {
    private lateinit var binding: ActivityCreateProfileBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var firestore: FirebaseFirestore
    private var imageBase64: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        // Initialize Firebase services
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference
        firestore = FirebaseFirestore.getInstance()

        // Show loading indicator while checking profile
        showLoading(true)

        // Check if profile data already exists
      //  checkProfileExists()

        setupClickListeners()
    }

    private fun showLoading(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.progressBar.visibility = View.VISIBLE
        }
    }

    private fun checkProfileExists() {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            // No user logged in, redirect to login
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }

        // Check in Firestore first
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    val profileImage = document.getString("profileImage")

                    // Check if essential profile data exists
                    if (!name.isNullOrEmpty()) {
                        Log.d("ProfileCheck", "Profile exists in Firestore, navigating to MainActivity")
                        navigateToMainActivity()
                        return@addOnSuccessListener
                    }
                }

                // If we get here, either document doesn't exist or missing essential data
                // Check Realtime Database as fallback
                database.child("users").child(userId).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists() && snapshot.child("name").exists()) {
                            Log.d("ProfileCheck", "Profile exists in Realtime DB, navigating to MainActivity")
                            navigateToMainActivity()
                        } else {
                            // No profile found, show the create profile UI
                            showLoading(false)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileCheck", "Error checking Realtime DB", e)
                        showLoading(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileCheck", "Error checking Firestore", e)
                showLoading(false)
            }
    }

    private fun setupClickListeners() {
        binding.ProfilePicture.setOnClickListener {
            pickImageFromGallery()
        }

        binding.profileInfoSaveButton.setOnClickListener {
            Toast.makeText(this, "Saving profile...", Toast.LENGTH_SHORT).show()
            saveProfileData()
        }

        binding.Skip.setOnClickListener {
            navigateToMainActivity()
        }
    }

    private fun pickImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            data.data?.let { uri ->
                binding.ProfilePicture.setImageURI(uri)
                imageBase64 = convertImageToBase64(uri)
            }
        }
    }

    private fun convertImageToBase64(uri: Uri): String {
        return try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val outputStream = ByteArrayOutputStream()

            // Compress image to reduce size
            bitmap.compress(Bitmap.CompressFormat.JPEG, 30, outputStream)

            Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to process image", Toast.LENGTH_SHORT).show()
            ""
        }
    }


    private fun saveProfileData() {
        val name = binding.profileName.text.toString().trim()
        val about = binding.MoreInfo.text.toString().trim()
        val userId = auth.currentUser?.uid ?: return

        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable the save button to prevent multiple submissions
        binding.profileInfoSaveButton.isEnabled = false

        val userData = hashMapOf(
            "name" to name,
            // Keep original case but add search-friendly version
            "searchName" to name.lowercase(),
            "about" to about,
            "profileImage" to (imageBase64 ?: ""),
            "timestamp" to System.currentTimeMillis(),
            "isActive" to true,
            "lastSeen" to System.currentTimeMillis()
        )

        // Save to Realtime Database
        database.child("users").child(userId).setValue(userData)
            .addOnSuccessListener {
                // Save to Firestore
                firestore.collection("users").document(userId).set(userData)
                    .addOnCompleteListener {
                        navigateToMainActivity()
                    }
                    .addOnFailureListener { e ->
                        Log.e("ProfileSave", "Failed to save to Firestore", e)
                        Toast.makeText(this, "Failed to save profile to Firestore", Toast.LENGTH_SHORT).show()
                        binding.profileInfoSaveButton.isEnabled = true
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileSave", "Failed to save to Realtime DB", e)
                Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show()
                binding.profileInfoSaveButton.isEnabled = true
            }
    }


    private fun navigateToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val PICK_IMAGE_REQUEST = 100
    }
}