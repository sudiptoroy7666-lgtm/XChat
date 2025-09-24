package com.example.xchat.ChatFunctions

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.xchat.R
import com.example.xchat.databinding.UserItemBinding

class UserAdapter(
    private var users: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: UserItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(user: User) {
            binding.userName.text = user.name

            // Load profile image
            when {
                user.profileImage.startsWith("http") -> {
                    Glide.with(binding.root.context)
                        .load(user.profileImage)
                        .placeholder(R.drawable.profilepicture)
                        .into(binding.userImage)
                }
                user.profileImage.isNotEmpty() -> { // Base64 image
                    try {
                        val imageBytes = Base64.decode(user.profileImage, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        binding.userImage.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        binding.userImage.setImageResource(R.drawable.profilepicture)
                    }
                }
                else -> {
                    binding.userImage.setImageResource(R.drawable.profilepicture)
                }
            }

            binding.root.setOnClickListener { onItemClick(user) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = UserItemBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(users[position])
    }

    override fun getItemCount() = users.size

    fun updateList(newList: List<User>) {
        users = newList
        notifyDataSetChanged()
        Log.d("ADAPTER", "Displaying ${newList.size} items")
    }
}