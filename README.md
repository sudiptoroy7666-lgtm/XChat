# 💬 XChat

> End-to-end encrypted peer chat with WebRTC audio/video calls and a built-in Gemini AI assistant

![Platform](https://img.shields.io/badge/Platform-Android-green?style=flat-square&logo=android)
![Language](https://img.shields.io/badge/Language-Kotlin-purple?style=flat-square&logo=kotlin)
![Firebase](https://img.shields.io/badge/Backend-Firebase-yellow?style=flat-square&logo=firebase)
![WebRTC](https://img.shields.io/badge/Calls-WebRTC-blue?style=flat-square)
![Min SDK](https://img.shields.io/badge/Min%20SDK-API%2024-blue?style=flat-square)

---

## Overview

XChat is a full-stack, one-to-one messaging app with three layers of real-time technology working simultaneously — Firebase for presence and message sync, Socket.IO for WebRTC signalling, and WebRTC itself for peer audio/video calls. All messages are encrypted end-to-end with AES keys derived from user credentials, so even Firebase cannot read the content. A built-in Gemini AI assistant lets users switch between human and AI chat in the same interface.

---

## ✨ Features

| Feature | Description |
|---|---|
| 🔒 AES E2E Encryption | Messages encrypted with per-user AES keys — unreadable even in Firebase |
| 📹 WebRTC Video Calls | Peer-to-peer video calls with low latency |
| 📞 WebRTC Audio Calls | Peer-to-peer voice calls via the same signalling pipeline |
| 🤝 Socket.IO Signalling | Custom signalling server for SDP/ICE exchange |
| 🤖 Gemini AI Assistant | In-app AI chat with isolated conversation thread |
| 👥 Friend Requests | Send, accept, and manage friend connections |
| 🖼️ Image Sharing | Send images within conversations |
| 📋 Call History Logs | View past audio and video call records |
| 🟢 Online Presence | Real-time online/offline status indicators |
| 👤 Profile Management | Update display name, photo, and bio |

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **Real-time DB:** Firebase Realtime Database + Firestore
- **Calls:** WebRTC (peer-to-peer)
- **Signalling:** Socket.IO
- **AI:** Google Gemini API
- **Encryption:** AES (javax.crypto)
- **Image Loading:** Glide
- **Min SDK:** API 24 (Android 7.0)

---

## 🏗️ Architecture

```
├── auth/
│   └── AuthActivity.kt
├── chat/
│   ├── ChatActivity.kt
│   ├── MessageAdapter.kt
│   └── ChatViewModel.kt
├── call/
│   ├── CallActivity.kt
│   ├── WebRTCClient.kt
│   └── SignallingClient.kt
├── ai/
│   └── GeminiChatActivity.kt
├── friends/
│   ├── FriendsListActivity.kt
│   └── FriendRequestAdapter.kt
└── utils/
    ├── AESEncryptionHelper.kt
    └── FirebaseHelper.kt
```

---

## 🔐 Security Model

```
User A                          Firebase                        User B
  │                                │                               │
  │── derives AES key from ────────┤                               │
  │   user credentials             │                               │
  │                                │                               │
  │── encrypts message ───────────▶│                               │
  │                          (ciphertext                           │
  │                          stored here)                          │
  │                                │────── delivers ciphertext ──▶│
  │                                │                               │
  │                                │                    decrypts with own key
```

AES keys are derived per-user from a combination of credentials — never stored in plaintext in Firebase or transmitted over the wire. A compromised Firebase database reveals only ciphertext.

---

## 💡 Implementation Highlights

**WebRTC Signalling**
WebRTC peer connection setup required a custom Socket.IO signalling server to exchange SDP offers/answers and ICE candidates between devices behind NAT. The signalling flow:
1. Caller creates an `RTCPeerConnection` and generates an SDP offer
2. Offer is sent via Socket.IO to the callee
3. Callee responds with an SDP answer
4. Both sides exchange ICE candidates until a peer path is found

**AI Isolation**
The Gemini AI integration maintains a fully isolated conversation thread — AI chat history is stored separately from human message history, preventing context contamination and ensuring the AI responses are not mixed into the real chat timeline.

**Encryption Key Derivation**
```kotlin
// Keys derived from credentials — never hardcoded or stored
val key = deriveKeyFromCredentials(userId, credentialHash)
val encrypted = AESHelper.encrypt(message, key)
```

---

## 📸 Screenshots

<p align="center">
  <img src="https://github.com/sudiptoroy7666-lgtm/portfolio/blob/fbc009ea41d89c1956497af02910183aa3dd1ecc/x1.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x2.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x3.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x4.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x5.jpg" width="18%"/>
</p>
<p align="center">
  <img src="assets/screenshots/xchat/x6.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x7.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x8.jpg" width="18%"/>
  <img src="assets/screenshots/xchat/x9.jpg" width="18%"/>
</p>

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Hedgehog or later
- A Firebase project
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/)
- A running Socket.IO signalling server (Node.js)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/sudiptoroy7666-lgtm/xchat.git
   cd xchat
   ```

2. **Configure Firebase**
   - Add Android app to your Firebase project
   - Enable Realtime Database and Firestore
   - Download `google-services.json` → place in `app/`

3. **Add your API keys in `local.properties`**
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   SIGNALLING_SERVER_URL=your_socket_io_server_url
   ```

4. **Build and run**
   ```bash
   ./gradlew assembleDebug
   ```

> ⚠️ **Never commit `local.properties` to version control.** It is already in `.gitignore`.

---

## 🔮 Future Improvements

- [ ] Group chat and group calls
- [ ] Signal-protocol-like double-ratchet key management
- [ ] TURN server deployment for better NAT traversal
- [ ] Message reactions and reply threads
- [ ] Read receipts and typing indicators

---

## 👤 Author

**Sudipta Roy**  
Android Developer | Java & Kotlin  
📧 sudiptoroy7666@gmail.com  
🔗 [Portfolio](https://sudiptoroy7666-lgtm.github.io/portfolio/) · [LinkedIn](https://www.linkedin.com/in/sudipta-roy-3873512b4/) · [GitHub](https://github.com/sudiptoroy7666-lgtm)
