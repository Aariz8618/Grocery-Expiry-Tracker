package com.aariz.expirytracker

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import android.widget.LinearLayout
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
    private val onItemClick: (GroceryItem) -> Unit,
    private val isGridView: Boolean = false
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridView) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_grocery_grid, parent, false)
                GroceryGridViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_grocery, parent, false)
                GroceryListViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroceryListViewHolder -> holder.bind(items[position])
            is GroceryGridViewHolder -> holder.bind(items[position])
        }
    }

    override fun getItemCount(): Int = items.size

    // List View Holder
    inner class GroceryListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_grocery_item)
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val quantity: TextView = itemView.findViewById(R.id.tv_quantity)
        private val location: TextView = itemView.findViewById(R.id.tv_location)
        private val expiryStatus: TextView = itemView.findViewById(R.id.tv_expiry_status)
        private val expiryStatusBadge: LinearLayout = itemView.findViewById(R.id.expiry_status_badge)
        private val statusBorder: View = itemView.findViewById(R.id.status_border)
        private val statusDot: View = itemView.findViewById(R.id.status_dot)
        private val iconContainer: FrameLayout = itemView.findViewById(R.id.icon_container)
        private val itemEmoji: TextView = itemView.findViewById(R.id.tv_item_emoji)

        fun bind(item: GroceryItem) {
            // Calculate real-time status
            val actualDaysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(actualDaysLeft, item.status)

            // Set item name
            itemName.text = item.name
            if (actualStatus == "used") {
                itemName.paintFlags = itemName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                itemName.paintFlags = itemName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // Set quantity
            quantity.text = item.quantity.toString() + if (item.weightUnit.isNotEmpty()) " ${item.weightUnit}" else " pcs"

            // Set location badge
            location.text = item.store.ifEmpty { "Storage" }
            location.setBackgroundResource(getLocationBadgeDrawable(item.store))
            location.setTextColor(ContextCompat.getColor(itemView.context, getLocationTextColor(item.store)))

            // Set expiry status and colors based on actual status
            when (actualStatus) {
                "used" -> {
                    expiryStatus.text = "Used"
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_orange)
                    statusDot.setBackgroundResource(R.drawable.status_dot_orange)
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange_500))
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_orange)
                }
                "expired" -> {
                    val daysAgo = Math.abs(actualDaysLeft)
                    expiryStatus.text = when {
                        daysAgo == 0 -> "Expired today"
                        daysAgo == 1 -> "Expired 1 day ago"
                        else -> "Expired $daysAgo days ago"
                    }
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_red)
                    statusDot.setBackgroundResource(R.drawable.status_dot_red)
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.red_500))
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_red)
                }
                "expiring" -> {
                    expiryStatus.text = when {
                        actualDaysLeft == 0 -> "Expires today"
                        actualDaysLeft == 1 -> "1 day left"
                        else -> "$actualDaysLeft days left"
                    }
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.amber_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_amber)
                    statusDot.setBackgroundResource(R.drawable.status_dot_amber)
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.amber_500))
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_amber)
                }
                else -> { // "fresh"
                    expiryStatus.text = "$actualDaysLeft days left"
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_green)
                    statusDot.setBackgroundResource(R.drawable.status_dot_green)
                    statusBorder.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                    iconContainer.setBackgroundResource(R.drawable.icon_bg_green)
                }
            }

            // Set item icon/emoji
            setItemIcon(item, actualStatus)

            // Set click listener
            cardView.setOnClickListener {
                onItemClick(item.copy(daysLeft = actualDaysLeft, status = actualStatus))
            }
        }

        private fun setItemIcon(item: GroceryItem, status: String) {
            if (item.imageUrl.isNotEmpty()) {
                // Hide emoji, show as ImageView placeholder
                itemEmoji.visibility = View.GONE
                // You can add ImageView loading here if needed
            } else {
                itemEmoji.visibility = View.VISIBLE
                itemEmoji.text = getCategoryEmoji(item.category)
            }
        }
    }

    // Grid View Holder
    inner class GroceryGridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.card_grocery_item)
        private val itemName: TextView = itemView.findViewById(R.id.tv_item_name)
        private val quantity: TextView = itemView.findViewById(R.id.tv_quantity)
        private val location: TextView = itemView.findViewById(R.id.tv_location)
        private val expiryStatus: TextView = itemView.findViewById(R.id.tv_expiry_status)
        private val expiryStatusBadge: LinearLayout = itemView.findViewById(R.id.tv_expiry_status_badge)
        private val statusBorderTop: View = itemView.findViewById(R.id.status_border_top)
        private val statusDot: View = itemView.findViewById(R.id.status_dot)
        private val topSection: View = itemView.findViewById(R.id.top_section)
        private val itemEmoji: TextView = itemView.findViewById(R.id.tv_item_emoji)
        private val itemImage: ImageView = itemView.findViewById(R.id.iv_item_image)

        fun bind(item: GroceryItem) {
            // Calculate real-time status
            val actualDaysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(actualDaysLeft, item.status)

            // Set item name
            itemName.text = item.name
            if (actualStatus == "used") {
                itemName.paintFlags = itemName.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                itemName.paintFlags = itemName.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }

            // Set quantity
            quantity.text = item.quantity.toString() + if (item.weightUnit.isNotEmpty()) " ${item.weightUnit}" else " pcs"

            // Set location badge
            location.text = item.store.ifEmpty { "Storage" }
            location.setBackgroundResource(getLocationBadgeDrawable(item.store))
            location.setTextColor(ContextCompat.getColor(itemView.context, getLocationTextColor(item.store)))

            // Set expiry status and colors based on actual status
            when (actualStatus) {
                "used" -> {
                    expiryStatus.text = "Used"
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.orange_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_orange)
                    statusDot.setBackgroundResource(R.drawable.status_dot_orange)
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.orange_500))
                    topSection.setBackgroundResource(R.drawable.top_section_bg_orange)
                }
                "expired" -> {
                    val daysAgo = Math.abs(actualDaysLeft)
                    expiryStatus.text = when {
                        daysAgo == 0 -> "Expired today"
                        daysAgo == 1 -> "Expired 1 day ago"
                        else -> "Expired $daysAgo days ago"
                    }
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.red_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_red)
                    statusDot.setBackgroundResource(R.drawable.status_dot_red)
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.red_500))
                    topSection.setBackgroundResource(R.drawable.top_section_bg_red)
                }
                "expiring" -> {
                    expiryStatus.text = when {
                        actualDaysLeft == 0 -> "Expires today"
                        actualDaysLeft == 1 -> "1 day left"
                        else -> "$actualDaysLeft days left"
                    }
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.amber_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_amber)
                    statusDot.setBackgroundResource(R.drawable.status_dot_amber)
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.amber_500))
                    topSection.setBackgroundResource(R.drawable.top_section_bg_amber)
                }
                else -> { // "fresh"
                    expiryStatus.text = "$actualDaysLeft days left"
                    expiryStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.green_700))
                    expiryStatusBadge.setBackgroundResource(R.drawable.status_badge_bg_green)
                    statusDot.setBackgroundResource(R.drawable.status_dot_green)
                    statusBorderTop.setBackgroundColor(ContextCompat.getColor(itemView.context, R.color.green_500))
                    topSection.setBackgroundResource(R.drawable.top_section_bg_green)
                }
            }

            // Set item icon/emoji or image from Firebase
            setItemIconOrImage(item)

            // Set click listener
            cardView.setOnClickListener {
                onItemClick(item.copy(daysLeft = actualDaysLeft, status = actualStatus))
            }
        }

        private fun setItemIconOrImage(item: GroceryItem) {
            if (item.imageUrl.isNotEmpty()) {
                // Load image from Firebase using Glide
                itemEmoji.visibility = View.GONE
                itemImage.visibility = View.VISIBLE

                Glide.with(itemView.context)
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder) 
                    .into(itemImage)
            } else {
                // Show emoji if no image
                itemImage.visibility = View.GONE
                itemEmoji.visibility = View.VISIBLE
                itemEmoji.text = getCategoryEmoji(item.category)
            }
        }
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            sdf.isLenient = false

            val expiry = sdf.parse(expiryDate) ?: return 0

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
        if (currentStatus == "used") return "used"
        return when {
            daysLeft < 0 -> "expired"
            daysLeft == 0 -> "expiring"
            daysLeft <= 3 -> "expiring"
            else -> "fresh"
        }
    }

    private fun getLocationBadgeDrawable(location: String): Int {
        return when (location.lowercase()) {
            "fridge" -> R.drawable.location_badge_blue
            "freezer" -> R.drawable.location_badge_cyan
            "pantry" -> R.drawable.location_badge_orange
            "counter" -> R.drawable.location_badge_purple
            else -> R.drawable.location_badge_blue
        }
    }

    private fun getLocationTextColor(location: String): Int {
        return when (location.lowercase()) {
            "fridge" -> R.color.blue_700
            "freezer" -> R.color.cyan_700
            "pantry" -> R.color.orange_700
            "counter" -> R.color.purple_700
            else -> R.color.blue_700
        }
    }

    private fun getCategoryEmoji(category: String): String {
        return when (category.lowercase()) {
            "fruits", "fruit" -> "🍎"
            "dairy" -> "🥛"
            "vegetables", "vegetable" -> "🥕"
            "meat" -> "🥩"
            "bakery" -> "🍞"
            "frozen" -> "🧊"
            "beverages" -> "🥤"
            "cereals" -> "🌾"
            "sweets" -> "🬬"
            else -> "🛒"
        }
    }
}