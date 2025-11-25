package com.aariz.expirytracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
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

class HomeFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: GroceryAdapter
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var greetingText: TextView
    private lateinit var profileButton: ImageView
    private lateinit var loadingIndicator: LinearLayout
    private lateinit var headerSection: LinearLayout
    private lateinit var adView: AdView

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
            Toast.makeText(requireContext(), "Notifications enabled! You'll receive expiry reminders.", Toast.LENGTH_LONG).show()
            scheduleNotifications()
        } else {
            Toast.makeText(requireContext(), "Notifications disabled. You won't receive expiry reminders.", Toast.LENGTH_LONG).show()
        }
        saveNotificationPermissionAsked()
    }

    // Activity Result Launchers
    private val addItemLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            loadGroceryItems()
        }
    }

    private val itemDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
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
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        } else {
            setupGreeting()
            loadProfileImage()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()
        firestore = FirebaseFirestore.getInstance()

        // Initialize Mobile Ads
        MobileAds.initialize(requireContext()) {}

        initViews(view)
        setupWindowInsets()
        setupAdView()
        setupRecyclerView()
        setupFilterButtons()
        setupFab()
        setupProfileButton()
        setupGreeting()
        loadGroceryItems()

        checkAndRequestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        adView.resume()
        loadGroceryItems()
        setupGreeting()
        loadProfileImage()
    }

    override fun onPause() {
        adView.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        adView.destroy()
        super.onDestroyView()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.recycler_items)
        emptyState = view.findViewById(R.id.empty_state)
        tvEmptyMessage = view.findViewById(R.id.tv_empty_message)
        greetingText = view.findViewById(R.id.tv_greeting)
        profileButton = view.findViewById(R.id.iv_profile)
        loadingIndicator = view.findViewById(R.id.loading_indicator)
        headerSection = view.findViewById(R.id.header_section)
        adView = view.findViewById(R.id.adView)

        // Initialize filter buttons
        btnAll = view.findViewById(R.id.btn_all)
        btnFresh = view.findViewById(R.id.btn_fresh)
        btnExpiring = view.findViewById(R.id.btn_expiring)
        btnExpired = view.findViewById(R.id.btn_expired)
        btnUsed = view.findViewById(R.id.btn_used)
    }

    private fun setupWindowInsets() {
        // Apply insets to header section using extension function
        headerSection.applyHeaderInsets()
    }

    private fun setupAdView() {
        val adRequest = AdRequest.Builder().build()
        adView.loadAd(adRequest)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val prefs = requireActivity().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            hasAskedForNotificationPermission = prefs.getBoolean("notification_permission_asked", false)

            val isGranted = ContextCompat.checkSelfPermission(
                requireContext(),
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
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Enable Notifications")
            .setMessage("Stay informed about items nearing expiry! Enable notifications to receive timely reminders.")
            .setPositiveButton("Enable") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Not Now") { _, _ ->
                saveNotificationPermissionAsked()
                Toast.makeText(requireContext(), "You can enable notifications later in Settings", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun saveNotificationPermissionAsked() {
        val prefs = requireActivity().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("notification_permission_asked", true).apply()
    }

    private fun scheduleNotifications() {
        val notificationScheduler = NotificationScheduler(requireContext())
        notificationScheduler.scheduleExpiryChecks()
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = GroceryAdapter(filteredGroceryItems) { item ->
            val intent = Intent(requireContext(), ItemDetailActivity::class.java).apply {
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
        btnAll.setOnClickListener { setFilter("all") }
        btnFresh.setOnClickListener { setFilter("fresh") }
        btnExpiring.setOnClickListener { setFilter("expiring") }
        btnExpired.setOnClickListener { setFilter("expired") }
        btnUsed.setOnClickListener { setFilter("used") }

        updateFilterButtonStates()
    }

    private fun setFilter(filter: String) {
        currentFilter = filter
        updateFilterButtonStates()
        applyFilter()
    }

    private fun updateFilterButtonStates() {
        resetButtonState(btnAll)
        resetButtonState(btnFresh)
        resetButtonState(btnExpiring)
        resetButtonState(btnExpired)
        resetButtonState(btnUsed)

        when (currentFilter) {
            "all" -> setActiveButtonState(btnAll)
            "fresh" -> setActiveButtonState(btnFresh)
            "expiring" -> setActiveButtonState(btnExpiring)
            "expired" -> setActiveButtonState(btnExpired)
            "used" -> setActiveButtonState(btnUsed)
        }
    }

    private fun resetButtonState(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_800))
        button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), android.R.color.white)
        button.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.green_primary)
    }

    private fun setActiveButtonState(button: MaterialButton) {
        button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        button.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.green_primary)
        button.strokeColor = ContextCompat.getColorStateList(requireContext(), R.color.green_primary)
    }

    private fun applyFilter() {
        filteredGroceryItems.clear()

        when (currentFilter) {
            "all" -> filteredGroceryItems.addAll(allGroceryItems)
            "fresh" -> filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                determineStatus(daysLeft, item.status) == "fresh"
            })
            "expiring" -> filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                determineStatus(daysLeft, item.status) == "expiring"
            })
            "expired" -> filteredGroceryItems.addAll(allGroceryItems.filter { item ->
                val daysLeft = calculateDaysLeft(item.expiryDate)
                determineStatus(daysLeft, item.status) == "expired"
            })
            "used" -> filteredGroceryItems.addAll(allGroceryItems.filter { it.status == "used" })
        }

        sortGroceryItems()
        adapter.notifyDataSetChanged()
        updateEmptyState()
    }

    private fun sortGroceryItems() {
        filteredGroceryItems.sortWith(compareBy<GroceryItem> { item ->
            val daysLeft = calculateDaysLeft(item.expiryDate)
            val status = determineStatus(daysLeft, item.status)
            when (status) {
                "expiring" -> 0
                "expired" -> 1
                "fresh" -> 2
                "used" -> 3
                else -> 4
            }
        }.thenBy { calculateDaysLeft(it.expiryDate) }
            .thenBy { it.name.lowercase() })
    }

    private fun setupFab() {
        view?.findViewById<FloatingActionButton>(R.id.fab_add_item)?.setOnClickListener {
            val intent = Intent(requireContext(), AddItemActivity::class.java)
            addItemLauncher.launch(intent)
        }
    }

    private fun setupProfileButton() {
        profileButton.setOnClickListener {
            val intent = Intent(requireContext(), ProfileActivity::class.java)
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
                            Glide.with(this)
                                .load(profileImageUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_user)
                                .error(R.drawable.ic_user)
                                .into(profileButton)
                        } else {
                            setProfileInitial()
                        }
                    } else {
                        setProfileInitial()
                    }
                }
                .addOnFailureListener { setProfileInitial() }
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

        val size = 40
        val sizePx = (size * resources.displayMetrics.density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = ContextCompat.getColor(requireContext(), R.color.green_primary)
        }
        canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)

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
                    Toast.makeText(requireContext(), "Failed to load items: ${error?.message}", Toast.LENGTH_LONG).show()
                    updateEmptyState()
                }
            } catch (e: Exception) {
                showLoading(false)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                updateEmptyState()
            }
        }
    }

    private fun updateEmptyState() {
        if (filteredGroceryItems.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyState.visibility = View.VISIBLE

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
        val prefs = requireActivity().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }
}