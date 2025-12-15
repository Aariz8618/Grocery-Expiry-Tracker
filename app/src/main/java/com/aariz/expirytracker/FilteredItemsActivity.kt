package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aariz.expirytracker.FirestoreRepository
import com.aariz.expirytracker.GroceryAdapter
import com.aariz.expirytracker.GroceryItem
import com.aariz.expirytracker.ItemDetailActivity
import com.aariz.expirytracker.R
import com.aariz.expirytracker.applyHeaderInsets
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class FilteredItemsActivity : AppCompatActivity() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var loadingIndicator: LinearLayout
    private lateinit var titleText: TextView

    private val groceryItems = mutableListOf<GroceryItem>()
    private var filterStatus: String = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_filtered_items)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        findViewById<View>(R.id.header_section).applyHeaderInsets()

        // Get filter type from intent
        filterStatus = intent.getStringExtra("filter_status") ?: "all"

        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupRecyclerView()
        loadFilteredItems()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_filtered_items)
        emptyState = findViewById(R.id.empty_state_filtered)
        loadingIndicator = findViewById(R.id.loading_indicator_filtered)
        titleText = findViewById(R.id.tv_filtered_title)

        // Set title based on filter
        val title = when (filterStatus) {
            "used" -> "Used Items ✓"
            "expired" -> "Expired Items ⚠️"
            "fresh" -> "Fresh Items 🌱"
            else -> "All Items"
        }
        titleText.text = title

        findViewById<Button>(R.id.button_back_filtered).setOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GroceryAdapter(
            items = groceryItems,
            onItemClick = { item ->
                val intent = Intent(this, ItemDetailActivity::class.java).apply {
                    putExtra("id", item.id)
                    putExtra("name", item.name)
                    putExtra("category", item.category)
                    putExtra("expiryDate", item.expiryDate)
                    putExtra("purchaseDate", item.purchaseDate)
                    putExtra("quantity", item.quantity)
                    putExtra("status", item.status)
                    putExtra("daysLeft", item.daysLeft)
                    putExtra("barcode", item.barcode)
                    putExtra("imageUrl", item.imageUrl)
                    putExtra("isGS1", item.isGS1)
                }
                startActivity(intent)
            },
            isGridView = false  // Optional, defaults to false
        )

        recyclerView.adapter = adapter
    }

    private fun loadFilteredItems() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()
                showLoading(false)

                if (result.isSuccess) {
                    val allItems = result.getOrNull() ?: emptyList()

                    // Recalculate status for each item
                    val itemsWithUpdatedStatus = allItems.map { item ->
                        val daysLeft = calculateDaysLeft(item.expiryDate)
                        val actualStatus = determineStatus(daysLeft, item.status)
                        item.copy(daysLeft = daysLeft, status = actualStatus)
                    }

                    // Filter items based on status
                    val filteredItems = when (filterStatus) {
                        "used" -> itemsWithUpdatedStatus.filter { it.status == "used" }
                        "expired" -> itemsWithUpdatedStatus.filter { it.status == "expired" }
                        "fresh" -> itemsWithUpdatedStatus.filter {
                            it.status == "fresh" || it.status == "expiring"
                        }
                        else -> itemsWithUpdatedStatus
                    }

                    groceryItems.clear()
                    groceryItems.addAll(filteredItems)
                    adapter.notifyDataSetChanged()

                    updateEmptyState()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(
                        this@FilteredItemsActivity,
                        "Failed to load items: ${error?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    updateEmptyState()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(
                    this@FilteredItemsActivity,
                    "Error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                updateEmptyState()
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

    private fun updateEmptyState() {
        if (groceryItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            val emptyMessage = findViewById<TextView>(R.id.tv_empty_message)
            emptyMessage.text = when (filterStatus) {
                "used" -> "No used items yet"
                "expired" -> "No expired items"
                "fresh" -> "No fresh items"
                else -> "No items found"
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyState.visibility = View.GONE
        }
    }

    private fun showLoading(show: Boolean) {
        loadingIndicator.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyState.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadFilteredItems()
    }
}