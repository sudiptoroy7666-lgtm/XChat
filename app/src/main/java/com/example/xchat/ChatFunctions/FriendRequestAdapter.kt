package com.example.xchat.ChatFunctions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xchat.R
import com.example.xchat.databinding.FriendRequestItemBinding

class FriendRequestAdapter(
    private var requests: List<FriendRequest>,
    private val onActionClick: (FriendRequest, Boolean) -> Unit
) : RecyclerView.Adapter<FriendRequestAdapter.ViewHolder>() {

    class ViewHolder(val binding: FriendRequestItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FriendRequestItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]

        holder.binding.senderNameTextView.text = request.senderName

        // Load profile image with Glide/Picasso/Coil
        Glide.with(holder.itemView.context)
            .load(request.senderProfileImage)
            .placeholder(R.drawable.profilepicture) // Placeholder image
            .into(holder.binding.senderProfileImageView)

        holder.binding.acceptButton.setOnClickListener {
            onActionClick(request, true)
        }

        holder.binding.rejectButton.setOnClickListener {
            onActionClick(request, false)
        }
    }


    override fun getItemCount() = requests.size

    fun updateList(newList: List<FriendRequest>) {
        requests = newList
        notifyDataSetChanged()
    }
}