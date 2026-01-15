package com.example.xchat.Ui

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.example.xchat.R
import android.util.Base64
import com.example.xchat.databinding.FragmentFriendProfileBinding
import com.google.firebase.firestore.FirebaseFirestore


        class FriendProfileFragment : Fragment() {
            private lateinit var binding: FragmentFriendProfileBinding
            private lateinit var firestore: FirebaseFirestore
            private var userId: String? = null

            override fun onCreateView(
                inflater: LayoutInflater, container: ViewGroup?,
                savedInstanceState: Bundle?
            ): View {
                binding = FragmentFriendProfileBinding.inflate(inflater, container, false)
                firestore = FirebaseFirestore.getInstance()
                userId = arguments?.getString("userId")
                loadFriendData()
                return binding.root
            }

            private fun loadFriendData() {
                userId?.let { uid ->
                    firestore.collection("users").document(uid).get()
                        .addOnSuccessListener { doc ->
                            if (doc.exists()) {
                                binding.nameText.text = doc.getString("name")
                                binding.statusText.text = doc.getString("status")
                                binding.emailText.text = doc.getString("email")


                                val profileImage = doc.getString("profileImage") ?: ""
                                if (profileImage.startsWith("http")) {
                                    Glide.with(this)
                                        .load(profileImage)
                                        .placeholder(R.drawable.profilepicture)
                                        .into(binding.profileImage)
                                } else if (profileImage.isNotEmpty()) {
                                    try {
                                        val imageBytes = Base64.decode(profileImage, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                        binding.profileImage.setImageBitmap(bitmap)
                                    } catch (e: Exception) {
                                        binding.profileImage.setImageResource(R.drawable.profilepicture)
                                    }
                                } else {
                                    binding.profileImage.setImageResource(R.drawable.profilepicture)
                                }
                            }
                        }
                }
            }
        }

