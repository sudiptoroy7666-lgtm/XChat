package com.example.xchat.Ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.xchat.CreateProfile

import com.example.xchat.ForgotPassword
import com.example.xchat.MainActivity
import com.example.xchat.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class LoginFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var forgotPasswordTextView: TextView

    private lateinit var rememberMeCheckbox: CheckBox
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_login, container, false)

        auth = FirebaseAuth.getInstance()
        sharedPreferences = requireActivity().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)


        emailEditText = view.findViewById(R.id.loginemail)
        passwordEditText = view.findViewById(R.id.loginpassword)
        loginButton = view.findViewById(R.id.loginButton)
        forgotPasswordTextView = view.findViewById(R.id.forgotPasswordTextView)
        rememberMeCheckbox = view.findViewById(R.id.rememberMeCheckbox)

        // Auto-login if "Remember Me" was checked previously
        if (sharedPreferences.getBoolean("remember_me", false)) {
            val savedEmail = sharedPreferences.getString("email", "")
            val savedPassword = sharedPreferences.getString("password", "")
            if (!savedEmail.isNullOrEmpty() && !savedPassword.isNullOrEmpty()) {
                emailEditText.setText(savedEmail)
                passwordEditText.setText(savedPassword)
                rememberMeCheckbox.isChecked = true
                loginUser(savedEmail, savedPassword)
            }
        }

        // Login Button Click
        loginButton.setOnClickListener {
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            loginUser(email, password)
        }

        // Forgot Password Click
        forgotPasswordTextView.setOnClickListener {
            startActivity(Intent(activity, ForgotPassword::class.java))
        }

        return view
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user?.isEmailVerified == true) {
                        // Save login state
                        if (rememberMeCheckbox.isChecked) {

                            sharedPreferences.edit().apply {
                                putBoolean("isLoggedIn", true)
                                putBoolean("remember_me", true)
                                putString("email", email)
                                putString("password", password)
                                apply()
                            }
                        } else {
                            sharedPreferences.edit().apply {
                                putBoolean("isLoggedIn", true)
                                putBoolean("remember_me", false)
                                apply()
                            }
                        }

                        // Check profile completeness
                        checkProfileCompleteness(user.uid)
                    } else {
                        Toast.makeText(
                            context,
                            "Please verify your email first.",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                    }
                } else {
                    Toast.makeText(
                        context,
                        "Login failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun checkProfileCompleteness(userId: String) {
        val firestore = FirebaseFirestore.getInstance()
        val database = FirebaseDatabase.getInstance().reference

        // Check Firestore first
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists() && document.getString("name")?.isNotEmpty() == true) {
                    navigateToMain()
                } else {
                    // Check Realtime Database
                    database.child("users").child(userId).get()
                        .addOnSuccessListener { snapshot ->
                            if (snapshot.exists() && snapshot.child("name").exists()) {
                                navigateToMain()
                            } else {
                                navigateToCreateProfile()
                            }
                        }
                        .addOnFailureListener {
                            navigateToCreateProfile()
                        }
                }
            }
            .addOnFailureListener {
                navigateToCreateProfile()
            }
    }

    private fun navigateToMain() {

        Toast.makeText(context, "Login successful!", Toast.LENGTH_SHORT).show()
        val intent = (Intent(activity, MainActivity::class.java))
        intent.putExtra("justLoggedIn", true) // 👈 Add this
        startActivity(intent)
        
        activity?.finish()
    }

    private fun navigateToCreateProfile() {
        startActivity(Intent(activity, CreateProfile::class.java))
        activity?.finish()
    }




}


