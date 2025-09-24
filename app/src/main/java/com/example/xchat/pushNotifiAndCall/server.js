const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

// Initialize Firebase Admin for authentication only
admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: "*",
    methods: ["GET", "POST"]
  }
});

// Store active connections
const activeConnections = new Map();
// Store pending messages for offline users
const pendingMessages = new Map();

io.on('connection', async (socket) => {
  // Handle registration
  socket.on('registerUser', (userId) => {
    console.log(`User registered: ${userId}`);
    activeConnections.set(userId, socket.id);

    // Send any pending messages
    if (pendingMessages.has(userId)) {
      const messages = pendingMessages.get(userId);
      messages.forEach(message => {
        socket.emit('newMessage', message);
      });
      pendingMessages.delete(userId); // Clear pending messages
    }
  });

  // Handle message events
  socket.on('newMessage', async (message) => {
    try {
      // Get sender info from Firestore (optional)
      const senderDoc = await admin.firestore()
        .collection('users')
        .doc(message.senderId)
        .get();

      const senderName = senderDoc.exists ? senderDoc.data().name : "User";

      // Prepare the message object
      const fullMessage = {
        ...message,
        senderName
      };

      const receiverSocketId = activeConnections.get(message.receiverId);

      if (receiverSocketId) {
        // Receiver is online - send via socket
        io.to(receiverSocketId).emit('newMessage', fullMessage);

        // Update message status to delivered
        socket.emit('messageStatus', {
          messageId: message.id,
          status: { [message.receiverId]: "delivered" }
        });
      } else {
        // Receiver is offline - store message
        if (!pendingMessages.has(message.receiverId)) {
          pendingMessages.set(message.receiverId, []);
        }
        pendingMessages.get(message.receiverId).push(fullMessage);
      }
    } catch (error) {
      console.error('Error handling message:', error);
    }
  });

  // Handle message status updates
  socket.on('messageRead', (data) => {
    const { messageId, senderId } = data;
    const senderSocketId = activeConnections.get(senderId);
    if (senderSocketId) {
      io.to(senderSocketId).emit('messageStatus', {
        messageId,
        status: { [data.userId]: "seen" }
      });
    }
  });

  socket.on('disconnect', () => {
    // Remove from active connections
    for (const [userId, socketId] of activeConnections.entries()) {
      if (socketId === socket.id) {
        activeConnections.delete(userId);
        console.log(`User disconnected: ${userId}`);
        break;
      }
    }
  });
});

server.listen(3000, () => {
  console.log('Server running on port 3000');
});