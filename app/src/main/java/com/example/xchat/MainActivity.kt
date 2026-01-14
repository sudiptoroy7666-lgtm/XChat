package com.example.xchat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.xchat.ChatFunctions.ChatActivity
import com.example.xchat.Call_feature.CallManager
import com.example.xchat.Call_feature.IncomingCallActivity
import com.example.xchat.MainFragments.CallFragment
import com.example.xchat.MainFragments.ChatFragment
import com.example.xchat.MainFragments.ProfileFragment
import com.example.xchat.Ui.AuthActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var firestore: FirebaseFirestore
    private lateinit var database: FirebaseDatabase


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        firestore = FirebaseFirestore.getInstance()
        database = FirebaseDatabase.getInstance()



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        val currentUser = auth.currentUser
        val isRemembered = sharedPreferences.getBoolean("remember_me", false)

        // Redirect to AuthActivity if not authenticated or not remembered (and not coming from login)
        if (currentUser == null || (!isRemembered && !isNewLoginIntent())) {
            startActivity(Intent(this, AuthActivity::class.java))
            finish()
            return
        }
// In MainActivity's onCreate
        if (intent.getBooleanExtra("navigateToChat", false)) {
            val userId = intent.getStringExtra("userId")
            userId?.let {
                val chatIntent = Intent(this, ChatActivity::class.java).apply {
                    putExtra("userId", it)
                }
                startActivity(chatIntent)
            }
        }
        // Check profile completeness for authenticated users
        checkProfileCompleteness(currentUser.uid)
        
        // SELF-HEALING: Reset call status to "available" on app launch
        // This ensures that if the app crashed or was killed while in a call,
        // the user doesn't get stuck in "in_call" status.
        FirebaseDatabase.getInstance().getReference("userStatus")
            .child(currentUser.uid)
            .child("callStatus")
            .setValue("available")


        // Initialize CallManager to listen for incoming calls
        val callManager = CallManager(this, currentUser.uid)
        callManager.startListeningForIncomingCalls { call ->
            val intent = IncomingCallActivity.newIntent(
                this,
                call.callId,
                call.callerId,
                call.callerName,
                call.isVideo
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }
    }

    private fun checkProfileCompleteness(userId: String) {
        // First check Firestore
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name")
                    if (!name.isNullOrEmpty()) {
                        // Profile is complete, show main UI
                        initializeMainUI()
                    } else {
                        // Profile exists but missing name, go to CreateProfile
                        redirectToCreateProfile()
                    }
                } else {
                    // Check Realtime Database if Firestore doesn't have data
                    checkRealtimeDatabaseProfile(userId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error checking Firestore", e)
                // Fallback to Realtime Database check
                checkRealtimeDatabaseProfile(userId)
            }
    }

    private fun checkRealtimeDatabaseProfile(userId: String) {
        database.reference.child("users").child(userId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists() && snapshot.child("name").exists()) {
                    // Profile is complete in Realtime DB
                    initializeMainUI()
                } else {
                    // Profile incomplete or doesn't exist
                    redirectToCreateProfile()
                }
            }
            .addOnFailureListener { e ->
                Log.e("MainActivity", "Error checking Realtime DB", e)
                // If we can't check, assume profile needs to be created
                redirectToCreateProfile()
            }
    }

    private fun initializeMainUI() {
        setContentView(R.layout.activity_main)

        val bottomNavigationMenu = findViewById<BottomNavigationView>(R.id.bnavbar)
        val chatFragment = ChatFragment()
        val callFragment = CallFragment()
        val profileFragment = ProfileFragment()

        setCurrentFragment(chatFragment)

        bottomNavigationMenu.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.user_chat -> setCurrentFragment(chatFragment)
                R.id.call -> setCurrentFragment(callFragment)
                R.id.profile_info -> setCurrentFragment(profileFragment)
            }
            true
        }
    }

    private fun redirectToCreateProfile() {
        startActivity(Intent(this, CreateProfile::class.java))
        finish()
    }

    private fun setCurrentFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun isNewLoginIntent(): Boolean {
        return intent.getBooleanExtra("justLoggedIn", false)
    }


}