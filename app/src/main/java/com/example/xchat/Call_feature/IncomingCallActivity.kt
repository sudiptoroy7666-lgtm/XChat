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
import com.example.xchat.Models.CallLog
import com.example.xchat.R
import com.example.xchat.databinding.ActivityIncomingCallBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase

import org.webrtc.SurfaceViewRenderer
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.media.MediaPlayer
import android.media.RingtoneManager

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
    private var mediaPlayer: MediaPlayer? = null
    private var callStartTime: Long = 0
    private var callerProfileImage: String = ""
    private var isCallEnded: Boolean = false




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
                Toast.makeText(this, "Permissions required for call", Toast.LENGTH_SHORT).show()
                declineCall()
            }
        }
    }

    private fun initializeCall() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            finish()
            return
        }

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


        binding.declineButton.setOnClickListener {
            declineCall()
        }

        binding.acceptButton.setOnClickListener {
            acceptCall()
        }
        
        binding.declineButtonInCall.setOnClickListener {
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

        // Start ringing timer
        startRingingTimer()
        
        // Start ringtone
        startRingtone()
    }

    private fun loadCallerProfileImage() {
        FirebaseFirestore.getInstance().collection("users").document(callerId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    callerProfileImage = document.getString("profileImage") ?: ""
                    if (callerProfileImage.startsWith("http")) {
                        Glide.with(this)
                            .load(callerProfileImage)
                            .placeholder(R.drawable.profilepicture)
                            .into(binding.callerImage)
                    } else if (callerProfileImage.isNotEmpty()) {
                        try {
                            val imageBytes = android.util.Base64.decode(callerProfileImage, android.util.Base64.DEFAULT)
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
        stopRingtone()
        callStartTime = System.currentTimeMillis()
        callDuration = 0
// In acceptCall()
        binding.timerText.visibility = View.VISIBLE
        // Update UI - hide ringing buttons, show in-call button
        binding.declineButton.visibility = View.GONE
        binding.acceptButton.visibility = View.GONE
        binding.declineButtonInCall.visibility = View.VISIBLE
        
        // Keep caller info visible but hide timer container if needed
        // Don't hide ringingContainer - it has caller name and image!
        binding.inCallContainer.visibility = View.VISIBLE

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
                        // Remote ended call
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

        // Answer the call
        callManager.answerCall(call)
    }

    private fun declineCall() {
        if (isCallEnded) return
        isCallEnded = true
        
        stopTimer()
        stopRingtone()
        
        // Reset call status to available
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        currentUserId?.let {
            FirebaseDatabase.getInstance().getReference("userStatus")
                .child(it)
                .child("callStatus")
                .setValue("available")
        }
        
        // Save as missed call
        saveCallLog("missed", 0)

        FirebaseDatabase.getInstance().getReference("ongoingCalls").child(callId)
            .child("status").setValue("declined")
            .addOnCompleteListener {
                finish()
            }
    }
    
    private fun endCall() {
        if (isCallEnded) return
        isCallEnded = true
        
        stopTimer()
        stopRingtone()
        
        // Reset call status to available
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        currentUserId?.let {
            FirebaseDatabase.getInstance().getReference("userStatus")
                .child(it)
                .child("callStatus")
                .setValue("available")
        }
        
        // Save call log with duration
        saveCallLog("incoming", callDuration)
        
        if (::callManager.isInitialized) {
            callManager.endCall()
        }
        
        FirebaseDatabase.getInstance().getReference("ongoingCalls").child(callId)
            .child("status").setValue("ended")
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
        stopRingtone()
        if (::callManager.isInitialized) {
            callManager.endCall()
        }
    }
    
    private fun startRingtone() {
        try {
            val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@IncomingCallActivity, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("IncomingCallActivity", "Error starting ringtone", e)
        }
    }
    
    private fun stopRingtone() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("IncomingCallActivity", "Error stopping ringtone", e)
        }
    }
    
    private fun saveCallLog(callType: String, duration: Long) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val firestore = FirebaseFirestore.getInstance()
        
        val callLog = CallLog(
            userId = callerId,
            userName = callerName,
            userProfileImage = callerProfileImage,
            callType = callType,
            isVideo = isVideoCall,
            timestamp = callStartTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            duration = duration
        )
        
        firestore.collection("users")
            .document(currentUserId)
            .collection("callLogs")
            .add(callLog)
    }
}