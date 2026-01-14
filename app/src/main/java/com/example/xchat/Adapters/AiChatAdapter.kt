package com.example.xchat.Adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.xchat.Models.ChatMessage
import com.example.xchat.R

class AiChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<AiChatAdapter.MessageViewHolder>() {

    class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val messageText: TextView = view.findViewById(R.id.messageText)
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].fromUser) R.layout.item_chat_user else R.layout.item_chat_bot
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.messageText.text = message.text
    }

    override fun getItemCount() = messages.size
}
