package com.example.xchat.ChatFunctions

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import com.example.xchat.R

class ImageViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)

        val fullScreenImageView = findViewById<ZoomableImageView>(R.id.fullScreenImageView)
        val imageBase64 = intent.getStringExtra("image_base64")

        imageBase64?.let {
            val imageBytes = Base64.decode(it, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            fullScreenImageView.setImageBitmap(bitmap)
        }
    }
}