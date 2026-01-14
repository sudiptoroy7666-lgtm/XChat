package com.example.xchat.Adapters

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xchat.Models.CallLog
import com.example.xchat.R
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class CallLogAdapter(
    private val callLogs: MutableList<CallLog>,
    private val onCallClick: (CallLog) -> Unit
) : RecyclerView.Adapter<CallLogAdapter.CallLogViewHolder>() {

    inner class CallLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val userProfileImage: CircleImageView = itemView.findViewById(R.id.userProfileImage)
        val userName: TextView = itemView.findViewById(R.id.userName)
        val callTypeIcon: ImageView = itemView.findViewById(R.id.callTypeIcon)
        val callTime: TextView = itemView.findViewById(R.id.callTime)
        val callButton: ImageButton = itemView.findViewById(R.id.callButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_call_log, parent, false)
        return CallLogViewHolder(view)
    }

    override fun onBindViewHolder(holder: CallLogViewHolder, position: Int) {
        val callLog = callLogs[position]
        
        // Set user name
        holder.userName.text = callLog.userName
        
        // Load profile image
        if (callLog.userProfileImage.startsWith("http")) {
            Glide.with(holder.itemView.context)
                .load(callLog.userProfileImage)
                .placeholder(R.drawable.profilepicture)
                .into(holder.userProfileImage)
        } else if (callLog.userProfileImage.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(callLog.userProfileImage, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                holder.userProfileImage.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.userProfileImage.setImageResource(R.drawable.profilepicture)
            }
        } else {
            holder.userProfileImage.setImageResource(R.drawable.profilepicture)
        }
        
        // Set call type icon
        val context = holder.itemView.context
        when (callLog.callType) {
            "incoming" -> {
                holder.callTypeIcon.setImageResource(R.drawable.ic_call_received)
                holder.callTypeIcon.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
            }
            "outgoing" -> {
                holder.callTypeIcon.setImageResource(R.drawable.ic_call_made)
                holder.callTypeIcon.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_green_dark)
                )
            }
            "missed" -> {
                holder.callTypeIcon.setImageResource(R.drawable.ic_call_missed)
                holder.callTypeIcon.setColorFilter(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
                holder.userName.setTextColor(
                    ContextCompat.getColor(context, android.R.color.holo_red_dark)
                )
            }
        }
        
        // Set timestamp
        holder.callTime.text = formatTimestamp(callLog.timestamp)
        
        // Set call button icon based on video/audio
        if (callLog.isVideo) {
            holder.callButton.setImageResource(R.drawable.ic_videocam)
        } else {
            holder.callButton.setImageResource(R.drawable.ic_call)
        }
        
        // Click listeners
        holder.callButton.setOnClickListener {
            onCallClick(callLog)
        }
        
        holder.itemView.setOnClickListener {
            // Could navigate to user profile or chat
        }
    }

    override fun getItemCount(): Int = callLogs.size

    fun updateCallLogs(newCallLogs: List<CallLog>) {
        callLogs.clear()
        callLogs.addAll(newCallLogs)
        notifyDataSetChanged()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val now = Calendar.getInstance()
        val callTime = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }

        return when {
            // Today
            now.get(Calendar.DAY_OF_YEAR) == callTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) -> {
                "Today, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            // Yesterday
            now.get(Calendar.DAY_OF_YEAR) - 1 == callTime.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) -> {
                "Yesterday, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            // This week
            now.get(Calendar.WEEK_OF_YEAR) == callTime.get(Calendar.WEEK_OF_YEAR) &&
            now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) -> {
                SimpleDateFormat("EEEE, h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            // This year
            now.get(Calendar.YEAR) == callTime.get(Calendar.YEAR) -> {
                SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault()).format(Date(timestamp))
            }
            // Previous years
            else -> {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}
