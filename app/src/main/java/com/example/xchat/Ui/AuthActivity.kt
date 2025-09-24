package com.example.xchat.Ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import com.example.xchat.R

class AuthActivity : AppCompatActivity() {
    private lateinit var tabSignIn: TextView
    private lateinit var tabSignUp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        supportActionBar?.hide()

        tabSignIn = findViewById(R.id.SignIn)
        tabSignUp = findViewById(R.id.Login)

        supportFragmentManager.beginTransaction()
            .replace(R.id.AuthFragmentContainer, LoginFragment())
            .commit()

        tabSignIn.setOnClickListener {
            highlightTab(true)
            supportFragmentManager.beginTransaction()
                .replace(R.id.AuthFragmentContainer, SignUpFragment())
                .commit()
        }

        tabSignUp.setOnClickListener {
            highlightTab(false)
            supportFragmentManager.beginTransaction()
                .replace(R.id.AuthFragmentContainer, LoginFragment())
                .commit()
        }
    }

    private fun highlightTab(isSignIn: Boolean) {
        tabSignIn.setTextColor(if (isSignIn) getColor(com.bumptech.glide.R.color.primary_dark_material_dark) else getColor(android.R.color.darker_gray))
        tabSignUp.setTextColor(if (!isSignIn) getColor(com.bumptech.glide.R.color.primary_dark_material_dark) else getColor(android.R.color.darker_gray))
    }
}