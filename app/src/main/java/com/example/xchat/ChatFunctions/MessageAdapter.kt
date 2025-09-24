package com.example.xchat.ChatFunctions

import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xchat.R
import com.example.xchat.databinding.MessageItemReceivedBinding
import com.example.xchat.databinding.MessageItemSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter(private val currentUserId: String) :
    ListAdapter<Message, MessageAdapter.BaseViewHolder>(DiffCallback()) {

    sealed class BaseViewHolder(viewGroup: ViewGroup) : RecyclerView.ViewHolder(viewGroup) {
        abstract fun bind(message: Message)
    }

    inner class SentViewHolder(private val binding: MessageItemSentBinding) : BaseViewHolder(binding.root) {
        override fun bind(message: Message) {





            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            binding.timeText.text = dateFormat.format(Date(message.timestamp))

// In both SentViewHolder and ReceivedViewHolder classes
            binding.imageMessage.setOnClickListener {
                if (message.type == "image") {
                    val context = binding.root.context
                    val intent = Intent(context, ImageViewActivity::class.java)
                    intent.putExtra("image_base64", message.imageBase64)
                    context.startActivity(intent)
                }
            }
            when (message.type) {
                "text" -> {
                    binding.messageText.visibility = View.VISIBLE
                    binding.imageMessage.visibility = View.GONE
                    binding.messageText.text = message.text
                }
                "image" -> {
                    binding.messageText.visibility = View.GONE
                    binding.imageMessage.visibility = View.VISIBLE
                    val imageBytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    Glide.with(binding.root)
                        .load(bitmap)
                        .into(binding.imageMessage)
                }
            }

            val status = message.status[currentUserId] ?: "sent"
            binding.statusText.text = when (status) {
                "seen" -> "✓✓"
                "delivered" -> "✓"
                else -> ""
            }
        }
    }

    inner class ReceivedViewHolder(private val binding: MessageItemReceivedBinding) : BaseViewHolder(binding.root) {
        override fun bind(message: Message) {
            val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            binding.timeText.text = dateFormat.format(Date(message.timestamp))

// In both SentViewHolder and ReceivedViewHolder classes
            binding.imageMessage.setOnClickListener {
                if (message.type == "image") {
                    val context = binding.root.context
                    val intent = Intent(context, ImageViewActivity::class.java)
                    intent.putExtra("image_base64", message.imageBase64)
                    context.startActivity(intent)
                }
            }
            when (message.type) {
                "text" -> {
                    binding.messageText.visibility = View.VISIBLE
                    binding.imageMessage.visibility = View.GONE
                    binding.messageText.text = message.text
                }
                "image" -> {
                    binding.messageText.visibility = View.GONE
                    binding.imageMessage.visibility = View.VISIBLE
                    val imageBytes = Base64.decode(message.imageBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    Glide.with(binding.root)
                        .load(bitmap)
                        .into(binding.imageMessage)
                }
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
        return when (viewType) {
            0 -> {
                val binding = MessageItemSentBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                SentViewHolder(binding)
            }
            else -> {
                val binding = MessageItemReceivedBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ReceivedViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).senderId == currentUserId) 0 else 1
    }

    class DiffCallback : DiffUtil.ItemCallback<Message>() {
        override fun areItemsTheSame(oldItem: Message, newItem: Message) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Message, newItem: Message) = oldItem == newItem
    }
}