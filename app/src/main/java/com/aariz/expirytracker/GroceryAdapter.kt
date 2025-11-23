package com.aariz.expirytracker

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class GroceryAdapter(
    private val items: List<GroceryItem>,
    private val onItemClick: (GroceryItem) -> Unit
) : RecyclerView.Adapter<GroceryAdapter.GroceryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroceryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_grocery, parent, false)
        return GroceryViewHolder(view)
    }

    override fun onBindViewHolder(holder: GroceryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class GroceryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_grocery_item)
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val category: TextView = itemView.findViewById(R.id.tv_category)
        private val expiryDate: TextView = itemView.findViewById(R.id.tv_expiry_date)
        private val quantity: TextView = itemView.findViewById(R.id.tv_quantity)
        private val statusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)
        private val statusIndicator: View = itemView.findViewById(R.id.status_indicator)
        private val itemIcon: ImageView = itemView.findViewById(R.id.iv_item_icon)

        fun bind(item: GroceryItem) {
            // RECALCULATE days left and status in real-time
            val actualDaysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(actualDaysLeft, item.status)

            // Bind all data
            itemName.text = item.name
            category.text = "Category: ${item.category}"
            expiryDate.text = "Expires: ${item.expiryDate}"
            quantity.text = "Qty: ${item.quantity}"

            // Load image if available, otherwise show category icon
            if (item.imageUrl.isNotEmpty()) {
                loadProductImage(item.imageUrl, item.category, actualStatus)
            } else {
                setItemIcon(item.category, actualStatus)
            }

            // Set status badge text, colors, and indicator based on RECALCULATED status
            when (actualStatus) {
                "used" -> {
                    statusBadge.text = "Used"
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.black))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_used)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange_400))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange_200))
                }
                "expired" -> {
                    val daysAgo = Math.abs(actualDaysLeft)
                    val expiredText = when {
                        daysAgo == 0 -> "Expired today"
                        daysAgo == 1 -> "Expired 1 day ago"
                        else -> "Expired $daysAgo days ago"
                    }

                    statusBadge.text = expiredText
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_expired)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.red_500))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
                "expiring" -> {
                    val daysText = when {
                        actualDaysLeft == 0 -> "Expires today"
                        actualDaysLeft == 1 -> "Expires in 1 day"
                        else -> "Expires in $actualDaysLeft days"
                    }

                    statusBadge.text = daysText
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_expiring)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.yellow_500))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
                else -> { // "fresh"
                    statusBadge.text = "Fresh ($actualDaysLeft days left)"
                    statusBadge.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    statusBadge.setBackgroundResource(R.drawable.status_badge_fresh)
                    statusIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                    cardView.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
            }

            // Set click listener with recalculated values
            cardView.setOnClickListener {
                onItemClick(item.copy(daysLeft = actualDaysLeft, status = actualStatus))
            }
        }

        private fun loadProductImage(imageUrl: String, category: String, status: String) {
            // IMPORTANT: Clear both color filter AND tint from XML
            itemIcon.clearColorFilter()
            itemIcon.imageTintList = null

            // Log for debugging
            android.util.Log.d("GroceryAdapter", "Loading image URL: $imageUrl")

            // Determine if it's a Cloudinary URL or a content URI
            val isCloudinaryUrl = imageUrl.startsWith("http://") || imageUrl.startsWith("https://")

            if (isCloudinaryUrl) {
                // Load from Cloudinary (or any web URL)
                Glide.with(itemView.context)
                    .load(imageUrl)
                    .centerCrop()
                    .placeholder(getCategoryIconResource(category))
                    .error(getCategoryIconResource(category))
                    .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<android.graphics.drawable.Drawable>,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.e("GroceryAdapter", "Failed to load image from URL: ${e?.message}")
                            e?.logRootCauses("GroceryAdapter")
                            // Fall back to category icon
                            setItemIcon(category, status)
                            return false
                        }

                        override fun onResourceReady(
                            resource: android.graphics.drawable.Drawable,
                            model: Any,
                            target: Target<android.graphics.drawable.Drawable>,
                            dataSource: com.bumptech.glide.load.DataSource,
                            isFirstResource: Boolean
                        ): Boolean {
                            android.util.Log.d("GroceryAdapter", "Image loaded successfully from: $dataSource")
                            return false
                        }
                    })
                    .into(itemIcon)
            } else if (imageUrl.startsWith("content://")) {
                // Try to load from content URI (legacy support for old items)
                try {
                    val uri = Uri.parse(imageUrl)
                    Glide.with(itemView.context)
                        .load(uri)
                        .centerCrop()
                        .placeholder(getCategoryIconResource(category))
                        .error(getCategoryIconResource(category))
                        .listener(object : RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: GlideException?,
                                model: Any?,
                                target: Target<android.graphics.drawable.Drawable>,
                                isFirstResource: Boolean
                            ): Boolean {
                                android.util.Log.e("GroceryAdapter", "Failed to load image from URI: ${e?.message}")
                                android.util.Log.w("GroceryAdapter", "Content URI may have expired. Consider re-uploading the image.")
                                // Fall back to category icon
                                setItemIcon(category, status)
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                model: Any,
                                target: Target<android.graphics.drawable.Drawable>,
                                dataSource: com.bumptech.glide.load.DataSource,
                                isFirstResource: Boolean
                            ): Boolean {
                                return false
                            }
                        })
                        .into(itemIcon)
                } catch (e: Exception) {
                    android.util.Log.e("GroceryAdapter", "Error parsing URI: ${e.message}")
                    // Fall back to category icon
                    setItemIcon(category, status)
                }
            } else {
                // Empty or invalid URL, show category icon
                android.util.Log.w("GroceryAdapter", "Invalid or empty image URL")
                setItemIcon(category, status)
            }
        }

        private fun calculateDaysLeft(expiryDate: String): Int {
            return try {
                val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                sdf.isLenient = false

                val expiry = sdf.parse(expiryDate) ?: return 0

                // Normalize both dates to midnight
                val expiryCalendar = Calendar.getInstance().apply {
                    time = expiry
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val todayCalendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

                val diffInMillis = expiryCalendar.timeInMillis - todayCalendar.timeInMillis
                TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
            } catch (e: Exception) {
                0
            }
        }

        private fun determineStatus(daysLeft: Int, currentStatus: String): String {
            // Don't change status if item is marked as "used"
            if (currentStatus == "used") return "used"

            return when {
                daysLeft < 0 -> "expired"
                daysLeft == 0 -> "expiring"
                daysLeft <= 3 -> "expiring"
                else -> "fresh"
            }
        }

        private fun setItemIcon(category: String, status: String) {
            val iconRes = getCategoryIconResource(category)
            itemIcon.setImageResource(iconRes)

            // Set icon tint based on status for visual hierarchy
            val tintColor = when (status) {
                "used" -> R.color.orange_400
                "expired" -> R.color.red_400
                "expiring" -> R.color.yellow_500
                else -> R.color.green_400
            }

            try {
                itemIcon.imageTintList = ContextCompat.getColorStateList(itemView.context, tintColor)
            } catch (e: Exception) {
                itemIcon.imageTintList = ContextCompat.getColorStateList(itemView.context, R.color.gray_600)
            }
        }

        private fun getCategoryIconResource(category: String): Int {
            return when (category.lowercase()) {
                "fruits", "fruit" -> getDrawableResource("fruit", R.drawable.fruits)
                "dairy" -> getDrawableResource("milk", R.drawable.milk)
                "vegetables", "vegetable" -> getDrawableResource("vegetable", R.drawable.vegetables)
                "meat" -> getDrawableResource("meat", R.drawable.meat)
                "bakery" -> getDrawableResource("breads", R.drawable.bread)
                "frozen" -> getDrawableResource("frozen", R.drawable.frozen)
                "beverages" -> getDrawableResource("beverages", R.drawable.beverages)
                "cereals" -> getDrawableResource("cereals", R.drawable.cereals)
                "sweets" -> getDrawableResource("sweets", R.drawable.sweets)
                else -> getDrawableResource("grocery", R.drawable.ic_grocery)
            }
        }

        private fun getDrawableResource(resourceName: String, fallback: Int): Int {
            return try {
                val context = itemView.context
                val resourceId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
                if (resourceId != 0) resourceId else fallback
            } catch (e: Exception) {
                fallback
            }
        }
    }
}