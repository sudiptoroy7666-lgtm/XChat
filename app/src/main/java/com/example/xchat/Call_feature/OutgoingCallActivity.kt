package com.example.xchat.Call_feature

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.xchat.Models.CallLog
import com.example.xchat.R
import com.example.xchat.databinding.ActivityOutgoingCallBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import org.webrtc.SurfaceViewRenderer
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class OutgoingCallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOutgoingCallBinding
    private lateinit var callManager: CallManager
    private lateinit var call: Call
    private var callId: String = ""
    private var receiverId: String = ""
    private var receiverName: String = ""
    private var isVideoCall: Boolean = false
    private var timerHandler = Handler(Looper.getMainLooper())
    private var callDuration: Long = 0
    private var timerRunning: Boolean = false
    private var callStartTime: Long = 0
    private var receiverProfileImage: String = ""
    private var isCallEnded: Boolean = false

    companion object {
        fun newIntent(
            context: Context,
            callId: String,
            receiverId: String,
            receiverName: String,
            isVideo: Boolean
        ): Intent {
            return Intent(context, OutgoingCallActivity::class.java).apply {
                putExtra("call_id", callId)
                putExtra("receiver_id", receiverId)
                putExtra("receiver_name", receiverName)
                putExtra("is_video", isVideo)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutgoingCallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        callId = intent.getStringExtra("call_id") ?: ""
        receiverId = intent.getStringExtra("receiver_id") ?: ""
        receiverName = intent.getStringExtra("receiver_name") ?: "Unknown"
        isVideoCall = intent.getBooleanExtra("is_video", false)

        // Check permissions before starting
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        } else {
            initializeCall()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initializeCall()
            } else {
                callManager.endCall()
                finish()
            }
        }
    }

    private fun initializeCall() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: throw IllegalStateException("User must be logged in to make calls")

        callManager = CallManager(this, currentUserId)

        // Setup UI
        binding.receiverName.text = receiverName
        binding.callType.text = if (isVideoCall) "Video Call" else "Audio Call"

        // Load receiver profile image
        loadReceiverProfileImage()

        // Setup call object
        call = Call(
            callId = callId,
            callerId = FirebaseAuth.getInstance().currentUser?.uid ?: "",
            receiverId = receiverId,
            callerName = "",
            receiverName = receiverName,
            isVideo = isVideoCall,
            status = "ringing"
        )

        // Setup button listeners
        binding.declineButton.setOnClickListener {
            endCall()
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

        // Start the call
        startCall()

        // Start ringing timer
        startRingingTimer()
    }

    private fun loadReceiverProfileImage() {
        FirebaseFirestore.getInstance().collection("users").document(receiverId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    receiverProfileImage = document.getString("profileImage") ?: ""
                    if (receiverProfileImage.startsWith("http")) {
                        Glide.with(this)
                            .load(receiverProfileImage)
                            .placeholder(R.drawable.profilepicture)
                            .into(binding.receiverImage)
                    } else if (receiverProfileImage.isNotEmpty()) {
                        try {
                            val imageBytes = android.util.Base64.decode(receiverProfileImage, android.util.Base64.DEFAULT)
                            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            binding.receiverImage.setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            binding.receiverImage.setImageResource(R.drawable.profilepicture)
                        }
                    }
                }
            }
    }

    private fun startCall() {
        // Get current user's name
        val currentUser = FirebaseAuth.getInstance().currentUser
        val currentUserUid = currentUser?.uid ?: ""

        FirebaseFirestore.getInstance().collection("users").document(currentUserUid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    call.callerName = document.getString("name") ?: "You"
                    binding.callerName.text = call.callerName

                    // Actually start the call
                    callManager.setOnCallStateChangedListener { state ->
                        runOnUiThread {
                            when (state) {
                                "accepted" -> {
                                    binding.callStatus.text = "Connected"
                                    startCallTimer()
                                }
                                "connected" -> {
                                    binding.callStatus.text = "Connected"
                                }
                                "ended", "declined" -> {
                                    // Remote ended request or declined
                                    if (!isCallEnded) {
                                        endCall()
                                    }
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

                    callManager.startCall(call, call.callerName)
                }
            }
    }

    private fun startRingingTimer() {
        timerRunning = true
        timerHandler.post(object : Runnable {
            override fun run() {
                if (!timerRunning) return

                callDuration += 1
                val seconds = callDuration % 60
                val minutes = callDuration / 60
                binding.timerText.text = String.format("%02d:%02d", minutes, seconds)

                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun startCallTimer() {
        callDuration = 0
        callStartTime = System.currentTimeMillis()
        timerHandler.post(object : Runnable {
            override fun run() {
                if (!timerRunning) return

                callDuration += 1
                val seconds = callDuration % 60
                val minutes = callDuration / 60
                binding.timerText.text = String.format("%02d:%02d", minutes, seconds)

                timerHandler.postDelayed(this, 1000)
            }
        })
    }

    private fun endCall() {
        if (isCallEnded) return
        isCallEnded = true
        
        timerRunning = false
        timerHandler.removeCallbacksAndMessages(null)
        
        // Reset call status to available
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        currentUserId?.let {
            FirebaseDatabase.getInstance().getReference("userStatus")
                .child(it)
                .child("callStatus")
                .setValue("available")
        }
        
        // Save call log
        saveCallLog()

        callManager.endCall()
        finish()
    }



    override fun onDestroy() {
        super.onDestroy()
        timerRunning = false
        timerHandler.removeCallbacksAndMessages(null)
        if (::callManager.isInitialized) {
            callManager.endCall()
        }
    }
    
    private fun saveCallLog() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        
        val callLog = CallLog(
            userId = receiverId,
            userName = receiverName,
            userProfileImage = receiverProfileImage,
            callType = "outgoing",
            isVideo = isVideoCall,
            timestamp = callStartTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            duration = callDuration
        )
        
        firestore.collection("users")
            .document(currentUserId)
            .collection("callLogs")
            .add(callLog)
    }
}