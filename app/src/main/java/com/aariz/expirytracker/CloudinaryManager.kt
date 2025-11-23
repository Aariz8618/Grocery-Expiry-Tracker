package com.aariz.expirytracker

import android.content.Context
import android.net.Uri
import android.util.Log
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback

object CloudinaryManager {
    private var initialized = false

    fun init(context: Context) {
        if (!initialized) {
            val config = mapOf(
                "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME
            )
            MediaManager.init(context, config)
            initialized = true
            Log.d("CloudinaryManager", "Initialized with cloud: ${BuildConfig.CLOUDINARY_CLOUD_NAME}")
        }
    }

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        uploadImage(context, imageUri, "profile_images", "profile_images", onSuccess, onError)
    }

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        uploadPreset: String,
        folder: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        init(context)

        try {
            MediaManager.get().upload(imageUri)
                .unsigned(uploadPreset)
                .option("folder", folder)
                .option("resource_type", "image")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {
                        Log.d("Cloudinary", "Upload started: $requestId")
                    }

                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {
                        val progress = if (totalBytes > 0) {
                            (bytes * 100 / totalBytes).toInt()
                        } else {
                            0
                        }
                        Log.d("Cloudinary", "Upload progress: $progress% ($bytes/$totalBytes)")
                    }

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        Log.d("Cloudinary", "Upload successful: $resultData")
                        val url = resultData["secure_url"] as? String
                        if (url != null) {
                            onSuccess(url)
                        } else {
                            Log.e("Cloudinary", "No secure_url in result")
                            onError("Failed to get image URL from response")
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        Log.e("Cloudinary", "Upload error: ${error.description}")
                        onError(error.description ?: "Unknown upload error")
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {
                        Log.w("Cloudinary", "Upload rescheduled: ${error.description}")
                        onError("Upload rescheduled: ${error.description}")
                    }
                })
                .dispatch()
        } catch (e: Exception) {
            Log.e("Cloudinary", "Exception during upload setup: ${e.message}", e)
            onError("Failed to start upload: ${e.message}")
        }
    }

    fun uploadFeedbackScreenshot(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        uploadImage(context, imageUri, "feedback_screenshots", "feedback_screenshots", onSuccess, onError)
    }

    fun uploadProductImage(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        uploadImage(context, imageUri, "Product_images", "product_images", onSuccess, onError)
    }
}