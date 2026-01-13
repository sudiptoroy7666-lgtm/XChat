package com.example.xchat.Call_feature

import android.content.Context
import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.Executors

class CallManager( context: Context, private val currentUserId: String ) {



    private val TAG = "CallManager"
    private val appContext = context.applicationContext
    private val database = FirebaseDatabase.getInstance()
    private val callRef = database.getReference("ongoingCalls")
    private val signalRef = database.getReference("callSignals")
    private val userStatusRef = database.getReference("userStatus")

    private var peerConnection: PeerConnection? = null
    private lateinit var localAudioTrack: AudioTrack
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var iceCandidates = mutableListOf<IceCandidate>()
    private var isCaller = false
    private var currentCallId: String? = null
    private var localSurfaceView: SurfaceViewRenderer? = null
    private var remoteSurfaceView: SurfaceViewRenderer? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val rootEglBase = EglBase.create()
    private lateinit var peerConnectionFactory: PeerConnectionFactory
    private lateinit var audioDeviceModule: JavaAudioDeviceModule

    private var onCallStateChanged: ((String) -> Unit)? = null
    private var onRemoteVideoReady: (() -> Unit)? = null

    init {
        // ✅ CRITICAL: Initialize WebRTC
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )
    }


    init {
        initializePeerConnectionFactory()
    }

    private fun initializePeerConnectionFactory() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        // Create audio device module
        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()

        val factory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(
                rootEglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(
                rootEglBase.eglBaseContext))
            .createPeerConnectionFactory()

        peerConnectionFactory = factory
    }

    fun setupLocalRenderer(surfaceView: SurfaceViewRenderer) {
        localSurfaceView = surfaceView
        localSurfaceView?.init(rootEglBase.eglBaseContext, null)
        localSurfaceView?.setEnableHardwareScaler(true)
    }

    fun setupRemoteRenderer(surfaceView: SurfaceViewRenderer) {
        remoteSurfaceView = surfaceView
        remoteSurfaceView?.init(rootEglBase.eglBaseContext, null)
        remoteSurfaceView?.setEnableHardwareScaler(true)
    }

    fun startCall(call: Call, localName: String) {
        isCaller = true
        currentCallId = call.callId



        // Create audio source & track
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

// Just add to peer connection — no sink needed for basic calls
        peerConnection?.addTrack(localAudioTrack, listOf("stream"))

        // If video call, setup local video
        if (call.isVideo) {
            setupLocalVideo()
        }

        // Create peer connection
        createPeerConnection(call)

        // Wait briefly before creating offer
        CoroutineScope(Dispatchers.Main).launch {
            android.os.Handler().postDelayed({
                createOffer(call)
            }, 500)
        }

        // Save call to database
        callRef.child(call.callId).setValue(call)

        // Setup signal listener
        setupSignalListener(call.callId)

        // Update user status
        updateCallStatus(call.callerId, "in_call")
    }

    private fun setupLocalVideo() {
        val videoCapturer = createVideoCapturer() ?: return

        val videoSource = peerConnectionFactory.createVideoSource(false)
        videoCapturer.initialize(
            SurfaceTextureHelper.create("CaptureThread", rootEglBase.eglBaseContext),
            appContext, videoSource.capturerObserver
        )
        videoCapturer.startCapture(1080, 720, 30)

        localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
        localVideoTrack?.addSink(localSurfaceView)
    }

    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            val cameraEnumerator = Camera2Enumerator(appContext)
            val deviceNames = cameraEnumerator.deviceNames

            for (deviceName in deviceNames) {
                if (cameraEnumerator.isFrontFacing(deviceName)) {
                    cameraEnumerator.createCapturer(deviceName, null)
                    break
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create video capturer: ${e.message}")
            null
        }
    }

    fun answerCall(call: Call) {
        isCaller = false
        currentCallId = call.callId

        // Create audio source & track
        val audioConstraints = MediaConstraints() // Can be empty
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)

        // If video call, setup local video
        if (call.isVideo) {
            setupLocalVideo()
        }

        // Create peer connection
        createPeerConnection(call)

        // Setup signal listener
        setupSignalListener(call.callId)

        // Update call status
        call.status = "accepted"
        callRef.child(call.callId).setValue(call)

        // Update user status
        updateCallStatus(call.receiverId, "in_call")

        onCallStateChanged?.invoke("accepted")
    }

    fun declineCall(callId: String) {
        val update = mapOf("status" to "declined")
        callRef.child(callId).updateChildren(update)
        cleanup()
    }

    fun endCall() {
        currentCallId?.let { callId ->
            val update = mapOf("status" to "ended")
            callRef.child(callId).updateChildren(update)
        }
        cleanup()
    }

    private fun createPeerConnection(call: Call) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (call.isVideo) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        peerConnection = peerConnectionFactory.createPeerConnection(
            PeerConnection.RTCConfiguration(iceServers),
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(TAG, "ICE Connection State: $state")
                    if (state == PeerConnection.IceConnectionState.CONNECTED) {
                        onCallStateChanged?.invoke("connected")
                    } else if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                        state == PeerConnection.IceConnectionState.FAILED) {
                        cleanup()
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {}

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(TAG, "ICE Gathering State: $state")
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate?.let {
                        currentCallId?.let { callId ->
                            val signal = SignalData(
                                type = "candidate",
                                callId = callId,
                                senderId = if (isCaller) call.callerId else call.receiverId,
                                candidate = it.sdp,
                                sdpMid = it.sdpMid,
                                sdpMLineIndex = it.sdpMLineIndex
                            )
                            signalRef.push().setValue(signal)
                        }
                    }
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

                override fun onAddStream(stream: MediaStream?) {
                    // Deprecated — use onAddTrack instead
                }

                override fun onRemoveStream(stream: MediaStream?) {}

                override fun onDataChannel(channel: DataChannel?) {}

                override fun onRenegotiationNeeded() {}

                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
                    if (mediaStreams.isEmpty()) return
                    val stream = mediaStreams[0]

                    // Audio is handled automatically by WebRTC
                    if (call.isVideo && stream.videoTracks.isNotEmpty()) {
                        remoteVideoTrack = stream.videoTracks[0]
                        remoteVideoTrack?.setEnabled(true)
                        remoteVideoTrack?.addSink(remoteSurfaceView)
                        onRemoteVideoReady?.invoke()
                    }
                }
            }
        )

        // Add local audio track
        peerConnection?.addTrack(localAudioTrack, listOf("stream"))

        // Add local video track if available
        if (call.isVideo && localVideoTrack != null) {
            peerConnection?.addTrack(localVideoTrack!!, listOf("stream"))
        }
    }

    private fun createOffer(call: Call) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (call.isVideo) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        peerConnection?.createOffer(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    Log.d(TAG, "Created offer: ${desc?.description}")

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Set local description success")
                            currentCallId?.let { callId ->
                                val signal = SignalData(
                                    type = "offer",
                                    callId = callId,
                                    senderId = call.callerId,
                                    sdp = desc?.description
                                )
                                signalRef.push().setValue(signal)
                            }
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                    }, desc)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Failed to create offer: $error")
                }
                override fun onSetFailure(error: String?) {}
            },
            constraints
        )
    }

    private fun createAnswer(offer: String, call: Call) {
        val sdp = SessionDescription(SessionDescription.Type.OFFER, offer)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Set remote description success")
                createAnswerSdp(call)
            }
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }
        }, sdp)
    }

    private fun createAnswerSdp(call: Call) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            if (call.isVideo) {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            }
        }

        peerConnection?.createAnswer(
            object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    Log.d(TAG, "Created answer: ${desc?.description}")

                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.d(TAG, "Set local description success")
                            currentCallId?.let { callId ->
                                val signal = SignalData(
                                    type = "answer",
                                    callId = callId,
                                    senderId = call.receiverId,
                                    sdp = desc?.description
                                )
                                signalRef.push().setValue(signal)
                            }
                        }
                        override fun onCreateFailure(error: String?) {}
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to set local description: $error")
                        }
                    }, desc)
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Failed to create answer: $error")
                }
                override fun onSetFailure(error: String?) {}
            },
            constraints
        )
    }

    private fun setupSignalListener(callId: String) {
        signalRef.orderByChild("callId").equalTo(callId)
            .addChildEventListener(object : com.google.firebase.database.ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val signal = snapshot.getValue(SignalData::class.java) ?: return

                    // Ignore own signals
                    val currentUserId = currentUserId
                    if (signal.senderId == currentUserId) return

                    when (signal.type) {
                        "offer" -> {
                            signal.sdp?.let { sdp ->
                                val call = Call(
                                    callId = signal.callId,
                                    callerId = signal.senderId,
                                    receiverId = currentUserId ?: "",
                                    isVideo = false,
                                    status = "ringing",
                                    timestamp = System.currentTimeMillis()
                                )
                                createAnswer(sdp, call)
                            }
                        }
                        "answer" -> {
                            signal.sdp?.let { sdp ->
                                peerConnection?.setRemoteDescription(object : SdpObserver {
                                    override fun onCreateSuccess(desc: SessionDescription?) {}
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "Answer set successfully")
                                    }
                                    override fun onCreateFailure(error: String?) {}
                                    override fun onSetFailure(error: String?) {
                                        Log.e(TAG, "Failed to set answer: $error")
                                    }
                                }, SessionDescription(SessionDescription.Type.ANSWER, sdp))
                            }
                        }
                        "candidate" -> {
                            signal.candidate?.let { candidateStr ->
                                val iceCandidate = IceCandidate(
                                    signal.sdpMid ?: "",
                                    signal.sdpMLineIndex ?: 0,
                                    candidateStr
                                )
                                peerConnection?.addIceCandidate(iceCandidate)
                            }
                        }
                    }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {}
            })

        // Listen for call status changes
        callRef.child(callId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val call = snapshot.getValue(Call::class.java)
                if (call?.status == "ended" || call?.status == "declined") {
                    cleanup()
                    onCallStateChanged?.invoke(call.status)
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateCallStatus(userId: String, status: String) {
        userStatusRef.child(userId).child("callStatus").setValue(status)
    }

    fun setOnCallStateChangedListener(listener: (String) -> Unit) {
        onCallStateChanged = listener
    }

    fun setOnRemoteVideoReadyListener(listener: () -> Unit) {
        onRemoteVideoReady = listener
    }

    private fun cleanup() {
        currentCallId?.let { callId ->
            // Update user status
            val currentUserId = currentUserId
            updateCallStatus(currentUserId ?: "", "available")

            // Remove call reference
            callRef.child(callId).removeValue()
        }

        // Clean up WebRTC resources
        remoteVideoTrack?.removeSink(remoteSurfaceView)
        localVideoTrack?.removeSink(localSurfaceView)

        peerConnection?.close()
        peerConnection = null

        localAudioTrack.setEnabled(false)
        localAudioTrack.dispose()

        localVideoTrack?.setEnabled(false)
        localVideoTrack?.dispose()

        remoteVideoTrack?.setEnabled(false)
        remoteVideoTrack?.dispose()

        localSurfaceView?.release()
        remoteSurfaceView?.release()

        audioDeviceModule.release()
        rootEglBase.release()

        executor.shutdown()
    }
}