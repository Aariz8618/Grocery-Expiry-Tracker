package com.aariz.expirytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DashboardActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var greetingText: TextView
    private lateinit var profileButton: ImageView
    private lateinit var loadingIndicator: LinearLayout
    private val groceryItems = mutableListOf<GroceryItem>()
    private lateinit var bottomNav: LinearLayout
    private lateinit var headerSection: LinearLayout

    // Filter buttons
    private lateinit var btnAll: MaterialButton
    private lateinit var btnFresh: MaterialButton
    private lateinit var btnExpiring: MaterialButton
    private lateinit var btnExpired: MaterialButton
    private lateinit var btnUsed: MaterialButton

    private val allGroceryItems = mutableListOf<GroceryItem>()
    private val filteredGroceryItems = mutableListOf<GroceryItem>()
    private var currentFilter = "all"

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var hasAskedForNotificationPermission = false

    // Notification permission launcher
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Notifications enabled! You'll receive expiry reminders.", Toast.LENGTH_LONG).show()
            scheduleNotifications()
        } else {
            Toast.makeText(this, "Notifications disabled. You won't receive expiry reminders.", Toast.LENGTH_LONG).show()
        }
        saveNotificationPermissionAsked()
    }

    // Activity Result Launchers
    private val addItemLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadGroceryItems()
        }
    }

    private val itemDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val itemUpdated = result.data?.getBooleanExtra("item_updated", false) ?: false
            val itemDeleted = result.data?.getBooleanExtra("item_deleted", false) ?: false

            if (itemUpdated || itemDeleted) {
                loadGroceryItems()
            }
        }
    }

    private val profileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            setupGreeting()
            loadProfileImage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_dashboard)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        MobileAds.initialize(this)

        val adView = findViewById<AdView>(R.id.adView)
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()
        firestore = FirebaseFirestore.getInstance()

        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()
        setupWindowInsets()
        setupRecyclerView()
        setupFilterButtons()
        setupNavigation()
        setupFab()
        setupProfileButton()
        setupGreeting()
        loadGroceryItems()

        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadGroceryItems()
        setupGreeting()
        loadProfileImage()
    }

    private fun setupWindowInsets() {
        // Apply insets to header section
        ViewCompat.setOnApplyWindowInsetsListener(headerSection) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                view.paddingBottom
            )
            insets
        }

        // Apply insets to bottom navigation
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                systemBars.bottom
            )
            insets
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            hasAskedForNotificationPermission = prefs.getBoolean("notification_permission_asked", false)

            val isGranted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                scheduleNotifications()
            } else if (!hasAskedForNotificationPermission) {
                showNotificationPermissionDialog()
            }
        } else {
            scheduleNotifications()
        }
    }

    private fun showNotificationPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Notifications")
            .setMessage("Stay informed about items nearing expiry! Enable notifications to receive timely reminders.")
            .setPositiveButton("Enable") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now") { _, _ ->
                saveNotificationPermissionAsked()
                Toast.makeText(this, "You can enable notifications later in Settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveNotificationPermissionAsked() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("notification_permission_asked", true).apply()
    }

    private fun scheduleNotifications() {
        val notificationScheduler = NotificationScheduler(this)
        notificationScheduler.scheduleExpiryChecks()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_items)
        emptyState = findViewById(R.id.empty_state)
        tvEmptyMessage = findViewById(R.id.tv_empty_message)
        greetingText = findViewById(R.id.tv_greeting)
        profileButton = findViewById(R.id.iv_profile)
        loadingIndicator = findViewById(R.id.loading_indicator)
        bottomNav = findViewById(R.id.bottom_nav)
        headerSection = findViewById(R.id.header_section)

        // Initialize filter buttons
        btnAll = findViewById(R.id.btn_all)
        btnFresh = findViewById(R.id.btn_fresh)
        btnExpiring = findViewById(R.id.btn_expiring)
        btnExpired = findViewById(R.id.btn_expired)
        btnUsed = findViewById(R.id.btn_used)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = GroceryAdapter(filteredGroceryItems) { item ->
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
            itemDetailLauncher.launch(intent)
        }

        recyclerView.adapter = adapter
    }

    private fun setupFilterButtons() {
        btnAll.setOnClickListener {
            setFilter("all")
        }

        btnFresh.setOnClickListener {
            setFilter("fresh")
        }

        btnExpiring.setOnClickListener {
            setFilter("expiring")
        }

        btnExpired.setOnClickListener {
            setFilter("expired")
        }

        btnUsed.setOnClickListener {
            setFilter("used")
        }

        // Set initial filter state
        updateFilterButtonStates()
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        updateFilterButtonStates()
        applyFilter()
    }

    private fun updateFilterButtonStates() {
        // Reset all buttons to default state
        resetButtonState(btnAll)
        resetButtonState(btnFresh)
        resetButtonState(btnExpiring)
        resetButtonState(btnExpired)
        resetButtonState(btnUsed)

        // Highlight the selected button
        when (currentFilter) {
            "all" -> setActiveButtonState(btnAll)
            "fresh" -> setActiveButtonState(btnFresh)
            "expiring" -> setActiveButtonState(btnExpiring)
            "expired" -> setActiveButtonState(btnExpired)
            "used" -> setActiveButtonState(btnUsed)
        }
    }

    private fun resetButtonState(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(this, R.color.gray_800))
        button.backgroundTintList = ContextCompat.getColorStateList(this, android.R.color.white)
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.green_primary)
    }

    private fun setActiveButtonState(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        button.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_primary)
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.green_primary)
    }

    private fun applyFilter() {
        filteredGroceryItems.clear()

        when (currentFilter) {
            "all" -> {
                filteredGroceryItems.addAll(allGroceryItems)
            }
            "fresh" -> {
                filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                    val daysLeft = calculateDaysLeft(item.expiryDate)
                    val status = determineStatus(daysLeft, item.status)
                    status == "fresh"
                })
            }
            "expiring" -> {
                filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                    val daysLeft = calculateDaysLeft(item.expiryDate)
                    val status = determineStatus(daysLeft, item.status)
                    status == "expiring"
                })
            }
            "expired" -> {
                filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                    val daysLeft = calculateDaysLeft(item.expiryDate)
                    val status = determineStatus(daysLeft, item.status)
                    status == "expired"
                })
            }
            "used" -> {
                filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                    item.status == "used"
                })
            }
        }

        // Sort items with priority: expiring → expired → fresh → used
        sortGroceryItems()

        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun sortGroceryItems() {
        filteredGroceryItems.sortWith(compareBy<GroceryItem> { item ->
            // Calculate real-time status
            val daysLeft = calculateDaysLeft(item.expiryDate)
            val status = determineStatus(daysLeft, item.status)

            // Priority order: expiring(0) → expired(1) → fresh(2) → used(3)
            when (status) {
                "expiring" -> 0
                "expired" -> 1
                "fresh" -> 2
                "used" -> 3
                else -> 4
            }
        }.thenBy { item ->
            // Within same status, sort by days left (ascending)
            calculateDaysLeft(item.expiryDate)
        }.thenBy { item ->
            // Finally, sort by name for consistency
            item.name.lowercase()
        })
    }

    private fun setupNavigation() {
        val tabHome = findViewById<LinearLayout>(R.id.tab_home)
        val tabStats = findViewById<LinearLayout>(R.id.tab_stats)
        val tabRecipes = findViewById<LinearLayout>(R.id.tab_recipes)
        val tabSettings = findViewById<LinearLayout>(R.id.tab_settings)

        tabHome.setOnClickListener {
            loadGroceryItems()
        }

        tabStats.setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        tabRecipes.setOnClickListener {
            startActivity(Intent(this, RecipesActivity::class.java))
        }

        tabSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setupFab() {
        val fab = findViewById<FloatingActionButton>(R.id.fab_add_item)
        fab.setOnClickListener {
            val intent = Intent(this, AddItemActivity::class.java)
            addItemLauncher.launch(intent)
        }
    }

    private fun setupProfileButton() {
        profileButton.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            profileLauncher.launch(intent)
        }
    }

    private fun setupGreeting() {
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email

        val greeting = when {
            !displayName.isNullOrEmpty() -> "Hello, $displayName 👋"
            !email.isNullOrEmpty() -> "Hello, ${email.substringBefore("@")} 👋"
            else -> "Hello, User 👋"
        }

        greetingText.text = greeting

        // Load profile image
        loadProfileImage()
    }

    private fun loadProfileImage() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val profileImageUrl = document.getString("profileImageUrl")
                        if (!profileImageUrl.isNullOrEmpty()) {
                            // Load profile image from Cloudinary
                            Glide.with(this)
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_user)
                                .error(R.drawable.ic_user)
                                .into(profileButton)
                        } else {
                            // No profile image, show initial letter
                            setProfileInitial()
                        }
                    } else {
                        // Document doesn't exist, show initial letter
                        setProfileInitial()
                    }
                }
                .addOnFailureListener { e ->
                    // On error, show initial letter
                    setProfileInitial()
                }
        }
    }

    private fun setProfileInitial() {
        val currentUser = auth.currentUser
        val displayName = currentUser?.displayName
        val email = currentUser?.email

        val initial = when {
            !displayName.isNullOrEmpty() -> displayName.firstOrNull()?.uppercase() ?: "U"
            !email.isNullOrEmpty() -> email.firstOrNull()?.uppercase() ?: "U"
            else -> "U"
        }

        // Create a bitmap with the initial letter
        val size = 40 // Match the ImageView size in dp (converted to px)
        val sizePx = (size * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // Draw circle background
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(this@DashboardActivity, R.color.green_primary)
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

        // Draw initial text
        val textPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = sizePx * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }

        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(initial, 0, initial.length, textBounds)
        val textY = sizePx / 2f - textBounds.exactCenterY()

        canvas.drawText(initial, sizePx / 2f, textY, textPaint)

        // Set the bitmap to ImageView
        profileButton.setImageBitmap(bitmap)
    }

    private fun loadGroceryItems() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()
                showLoading(false)

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    allGroceryItems.clear()
                    allGroceryItems.addAll(items)

                    applyFilter()
                } else {
                    val error = result.exceptionOrNull()
                    Toast.makeText(this@DashboardActivity, "Failed to load items: ${error?.message}", Toast.LENGTH_LONG).show()
                    updateEmptyState()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(this@DashboardActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        if (filteredGroceryItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

            // Update empty message based on filter
            val message = when (currentFilter) {
                "all" -> "No items added yet"
                "fresh" -> "No fresh items"
                "expiring" -> "No items expiring soon"
                "expired" -> "No expired items"
                "used" -> "No used items"
                else -> "No items found"
            }
            tvEmptyMessage.text = message
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

    private fun clearUserLoggedInFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }
}