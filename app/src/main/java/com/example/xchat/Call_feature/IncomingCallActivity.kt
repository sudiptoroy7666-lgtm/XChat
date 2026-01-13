package com.example.xchat.Call_feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.xchat.R
import com.example.xchat.databinding.ActivityIncomingCallBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import org.webrtc.SurfaceViewRenderer

class IncomingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIncomingCallBinding
    private lateinit var callManager: CallManager
    private lateinit var call: Call
    private var callId: String = ""
    private var callerId: String = ""
    private var callerName: String = ""
    private var isVideoCall: Boolean = false
    private var timerHandler = Handler(Looper.getMainLooper())
    private var callDuration: Long = 0
    private var timerRunning: Boolean = false




    companion object {
        const val ACTION_DECLINE = "decline_call"
        const val ACTION_ACCEPT = "accept_call"

        fun newIntent(
            context: Context,
            callId: String,
            callerId: String,
            callerName: String,
            isVideo: Boolean
        ): Intent {
            return Intent(context, IncomingCallActivity::class.java).apply {
                putExtra("call_id", callId)
                putExtra("caller_id", callerId)
                putExtra("caller_name", callerName)
                putExtra("is_video", isVideo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIncomingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callId = intent.getStringExtra("call_id") ?: ""
        callerId = intent.getStringExtra("caller_id") ?: ""
        callerName = intent.getStringExtra("caller_name") ?: "Unknown"
        isVideoCall = intent.getBooleanExtra("is_video", false)

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User must be logged in to make calls")

        callManager = CallManager(this, currentUserId)

        // Setup UI
        binding.callerName.text = callerName
        binding.callType.text = if (isVideoCall) "Video Call" else "Audio Call"

        // Load caller profile image
        loadCallerProfileImage()

        // Setup call object for later use
        call = Call(
            callId = callId,
            callerId = callerId,
            receiverId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            callerName = callerName,
            receiverName = "",
            isVideo = isVideoCall,
            status = "ringing"
        )


        // Setup button listeners
        binding.declineButton.setOnClickListener {
            declineCall()
        }

        binding.acceptButton.setOnClickListener {
            acceptCall()
        }

        // Setup surface views for video calls
        if (isVideoCall) {
            binding.remoteRenderer.visibility = View.VISIBLE
            binding.localRenderer.visibility = View.VISIBLE

            callManager.setupRemoteRenderer(binding.remoteRenderer)
            callManager.setupLocalRenderer(binding.localRenderer)
        } else {
            binding.remoteRenderer.visibility = View.GONE
            binding.localRenderer.visibility = View.GONE
        }

        // Start ringing timer
        startRingingTimer()
    }

    private fun loadCallerProfileImage() {
        FirebaseFirestore.getInstance().collection("users").document(callerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val profileImage = document.getString("profileImage") ?: ""
                    if (profileImage.startsWith("http")) {
                        Glide.with(this)
                            .load(profileImage)
                            .placeholder(R.drawable.profilepicture)
                            .into(binding.callerImage)
                    } else if (profileImage.isNotEmpty()) {
                        try {
                            val imageBytes = android.util.Base64.decode(profileImage, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.callerImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.callerImage.setImageResource(R.drawable.profilepicture)
                        }
                    }
                }
            }
    }

    // ✅ CORRECTED CODE SNIPPETS

    // Start timer
    private fun startRingingTimer() {
        timerRunning = true
        timerHandler.post(object : Runnable {
            override fun run() {
                if (!timerRunning) return

                callDuration += 1
                val seconds = callDuration % 60
                val minutes = callDuration / 60
                binding.timerText.text = String.format("%02d:%02d", minutes, seconds) // ✅ Now works

                if (callDuration >= 30) {
                    declineCall()
                    return
                }

                timerHandler.postDelayed(this, 1000)
            }
        })
    }



    private fun stopTimer() {
        timerRunning = false
        timerHandler.removeCallbacksAndMessages(null)
    }

    private fun acceptCall() {
        stopTimer()
// In acceptCall()
        binding.timerText.visibility = View.VISIBLE
        // Update UI
        binding.ringingContainer.visibility = View.GONE
        binding.inCallContainer.visibility = View.VISIBLE
        binding.declineButtonInCall.visibility = View.VISIBLE

        // Set up state listeners
        callManager.setOnCallStateChangedListener { state ->
            when (state) {
                "accepted" -> {
                    runOnUiThread {
                        binding.callStatus.text = "Connected"
                        binding.timerText.visibility = View.VISIBLE
                        timerHandler.post(object : Runnable {
                            override fun run() {
                                if (timerRunning) {
                                    callDuration += 1
                                    val seconds = callDuration % 60
                                    val minutes = callDuration / 60
                                    binding.timerText.text = String.format("%02d:%02d", minutes, seconds)
                                    timerHandler.postDelayed(this, 1000) // ← No "binding." here!
                                }
                            }
                        })
                    }
                }
                "connected" -> {
                    runOnUiThread {
                        binding.callStatus.text = "Connected"
                    }
                }
                "ended", "declined" -> {
                    runOnUiThread {
                        finish()
                    }
                }
            }
        }

        if (isVideoCall) {
            callManager.setOnRemoteVideoReadyListener {
                runOnUiThread {
                    binding.remoteRenderer.visibility = View.VISIBLE
                }
            }
        }

        // Answer the call
        callManager.answerCall(call)
    }

    private fun declineCall() {
        stopTimer()

        FirebaseDatabase.getInstance().getReference("ongoingCalls").child(callId)
            .child("status").setValue("declined")
            .addOnCompleteListener {
                finish()
            }
    }

    override fun onBackPressed() {
        declineCall()
        super.onBackPressed() // ✅ Required
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        if (::callManager.isInitialized) {
            callManager.endCall()
        }
    }
}