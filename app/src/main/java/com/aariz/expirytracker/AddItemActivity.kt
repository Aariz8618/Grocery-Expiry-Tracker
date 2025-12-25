package com.aariz.expirytracker

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class AddItemActivity : AppCompatActivity() {

    private var quantity = 1
    private lateinit var qtyText: TextView
    private lateinit var inputName: EditText
    private lateinit var textCategory: TextView
    private lateinit var textPurchaseDate: TextView
    private lateinit var textExpiryDate: TextView
    private lateinit var inputAmount: EditText
    private lateinit var inputWeight: EditText
    private lateinit var textWeightUnit: TextView
    private lateinit var weightUnitSelector: LinearLayout
    private lateinit var textStorageLocation: TextView
    private lateinit var inputNotes: EditText
    private lateinit var additionalInfoHeader: LinearLayout
    private lateinit var additionalInfoContent: LinearLayout
    private lateinit var additionalInfoChevron: ImageView
    private lateinit var saveButton: MaterialCardView
    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var productImageView: ImageView
    private lateinit var barcodeInfoLayout: MaterialCardView
    private lateinit var barcodeText: TextView
    private lateinit var gs1InfoLayout: MaterialCardView
    private lateinit var gs1InfoText: TextView
    private lateinit var addImageButton: MaterialCardView
    private lateinit var removeImageButton: MaterialCardView
    private lateinit var buttonScan: MaterialCardView
    private lateinit var buttonDecrement: ImageView
    private lateinit var buttonIncrement: ImageView
    private var selectedCategory: String = ""
    private var selectedPurchaseDate: String = ""
    private var selectedExpiryDate: String = ""
    private var selectedStorageLocation: String = ""
    private var selectedWeightUnit: String = "kg"
    private var scannedBarcode: String = ""
    private var productImageUrl: String = ""
    private var userUploadedImageUri: Uri? = null
    private var isGS1Code: Boolean = false
    private var isAdditionalInfoExpanded: Boolean = false

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    // Activity Result Launcher for image picker
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleImageSelection(it)
        }
    }

    // Activity Result Launcher for barcode scanner
    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            handleBarcodeResult(result.data!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_add_item)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply insets AFTER views are initialized
        if (auth.currentUser == null) {
            Toast.makeText(this, "Please login to add items", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupClickListeners()
        setupInitialValues()
        setupBackPressedHandler()
        createUserProfileIfNeeded()

        // Apply window insets - moved here after initViews()
        applyWindowInsets()
    }

    private fun applyWindowInsets() {
        // Apply top inset to the header
        findViewById<View>(R.id.header_section).applyHeaderInsets()

        // Apply bottom inset to the bottom bar (not just the button)
        findViewById<View>(R.id.bottom_bar).applyBottomNavInsets()
    }

    private fun createUserProfileIfNeeded() {
        val currentUser = auth.currentUser ?: return

        lifecycleScope.launch {
            try {
                val user = User(
                    id = currentUser.uid,
                    firstName  = currentUser.displayName ?: "User",
                    email = currentUser.email ?: ""
                )
                firestoreRepository.createUserProfile(user)
            } catch (e: Exception) {
                Log.e("AddItemActivity", "Error creating user profile: ${e.message}")
            }
        }
    }

    private fun initViews() {
        // Basic fields
        qtyText = findViewById(R.id.input_quantity)
        inputName = findViewById(R.id.input_name)
        textCategory = findViewById(R.id.text_category)
        textPurchaseDate = findViewById(R.id.text_purchase_date)
        textExpiryDate = findViewById(R.id.text_expiry_date)

        // New fields
        inputAmount = findViewById(R.id.input_amount)
        inputWeight = findViewById(R.id.input_weight)
        textWeightUnit = findViewById(R.id.text_weight_unit)
        weightUnitSelector = findViewById(R.id.weight_unit_selector)

        // Additional information
        textStorageLocation = findViewById(R.id.text_storage_location)
        inputNotes = findViewById(R.id.input_notes)
        additionalInfoHeader = findViewById(R.id.additional_info_header)
        additionalInfoContent = findViewById(R.id.additional_info_content)
        additionalInfoChevron = findViewById(R.id.additional_info_chevron)

        // UI elements
        saveButton = findViewById(R.id.button_save_item)
        loadingOverlay = findViewById(R.id.loading_overlay)
        progressBar = findViewById(R.id.progress_bar)
        productImageView = findViewById(R.id.product_image_view)
        barcodeInfoLayout = findViewById(R.id.barcode_info_layout)
        barcodeText = findViewById(R.id.barcode_text)
        gs1InfoLayout = findViewById(R.id.gs1_info_layout)
        gs1InfoText = findViewById(R.id.gs1_info_text)
        addImageButton = findViewById(R.id.add_image_button)
        removeImageButton = findViewById(R.id.remove_image_button)
        buttonScan = findViewById(R.id.button_scan)

        // Increment/Decrement buttons
        buttonDecrement = findViewById(R.id.button_decrement)
        buttonIncrement = findViewById(R.id.button_increment)

        // Add text capitalization watcher
        inputName.addTextChangedListener(TextCapitalizationWatcher())
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Quantity buttons - now ImageViews
        buttonDecrement.setOnClickListener {
            if (quantity > 1) quantity--
            qtyText.text = quantity.toString()
        }

        buttonIncrement.setOnClickListener {
            quantity++
            qtyText.text = quantity.toString()
        }

        // Category selector
        findViewById<MaterialCardView>(R.id.category_container).setOnClickListener {
            showCategoryDialog()
        }

        // Date pickers
        findViewById<MaterialCardView>(R.id.purchase_date_container).setOnClickListener {
            showDatePicker(true)
        }

        findViewById<MaterialCardView>(R.id.expiry_date_container).setOnClickListener {
            showDatePicker(false)
        }

        // Weight unit selector
        weightUnitSelector.setOnClickListener {
            showWeightUnitDialog()
        }

        // Storage location selector
        findViewById<MaterialCardView>(R.id.storage_location_container).setOnClickListener {
            showStorageLocationDialog()
        }

        // Additional information toggle
        additionalInfoHeader.setOnClickListener {
            toggleAdditionalInfo()
        }

        // Barcode scanner button
        buttonScan.setOnClickListener {
            launchBarcodeScanner()
        }

        // Save button
        saveButton.setOnClickListener {
            saveItemToFirestore()
        }

        // Clear barcode info
        findViewById<ImageView>(R.id.clear_barcode_button).setOnClickListener {
            clearBarcodeInfo()
        }

        // Add image button
        addImageButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        // Remove image button
        removeImageButton.setOnClickListener {
            removeImage()
        }
    }

    private fun toggleAdditionalInfo() {
        isAdditionalInfoExpanded = !isAdditionalInfoExpanded

        if (isAdditionalInfoExpanded) {
            additionalInfoContent.visibility = View.VISIBLE
            additionalInfoChevron.rotation = 180f
        } else {
            additionalInfoContent.visibility = View.GONE
            additionalInfoChevron.rotation = 0f
        }
    }

    private fun showWeightUnitDialog() {
        val units = arrayOf("kg", "g", "lb", "oz")
        val currentIndex = units.indexOf(selectedWeightUnit).takeIf { it >= 0 } ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Weight Unit")
            .setSingleChoiceItems(units, currentIndex) { dialog, which ->
                selectedWeightUnit = units[which]
                textWeightUnit.text = selectedWeightUnit
                dialog.dismiss()
            }
            .show()
    }

    private fun showStorageLocationDialog() {
        val locations = arrayOf(
            "Refrigerator",
            "Freezer",
            "Pantry",
            "Kitchen Cabinet",
            "Dining Room",
            "Other"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Storage Location")
            .setItems(locations) { _, which ->
                selectedStorageLocation = locations[which]
                textStorageLocation.text = selectedStorageLocation
                textStorageLocation.setTextColor(getColor(R.color.gray_800))
            }
            .show()
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            userUploadedImageUri = uri
            displayImage(uri.toString(), isUserUploaded = true)
            Toast.makeText(this, "Image added successfully", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("AddItemActivity", "Error loading image: ${e.message}")
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeImage() {
        userUploadedImageUri = null
        productImageUrl = ""
        hideImage()
        Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show()
    }

    private fun displayImage(imageUrl: String, isUserUploaded: Boolean) {
        productImageView.visibility = View.VISIBLE
        addImageButton.visibility = View.GONE
        removeImageButton.visibility = View.VISIBLE

        Glide.with(this)
            .load(imageUrl)
            .placeholder(android.R.drawable.ic_menu_gallery)
            .error(android.R.drawable.ic_menu_gallery)
            .into(productImageView)
    }

    private fun hideImage() {
        productImageView.visibility = View.GONE
        addImageButton.visibility = View.VISIBLE
        removeImageButton.visibility = View.GONE
    }

    private fun launchBarcodeScanner() {
        val intent = Intent(this, BarcodeScannerActivity::class.java)
        barcodeScannerLauncher.launch(intent)
    }
    private fun handleBarcodeResult(data: Intent) {
        val barcode = data.getStringExtra("barcode") ?: ""
        val originalData = data.getStringExtra("original_data") ?: ""
        val barcodeFormat = data.getStringExtra("barcode_format") ?: ""
        val isGS1 = data.getBooleanExtra("is_gs1", false)

        // GS1 specific data
        val gs1ExpiryDate = data.getStringExtra("gs1_expiry_date") ?: ""
        val gs1BatchLot = data.getStringExtra("gs1_batch_lot") ?: ""
        val gs1SerialNumber = data.getStringExtra("gs1_serial_number") ?: ""
        val gs1ProductionDate = data.getStringExtra("gs1_production_date") ?: ""
        val gs1BestBeforeDate = data.getStringExtra("gs1_best_before_date") ?: ""
        val gs1GTIN = data.getStringExtra("gs1_gtin") ?: ""

        // Product lookup results
        val productFound = data.getBooleanExtra("product_found", false)
        val productName = data.getStringExtra("product_name") ?: ""
        val brands = data.getStringExtra("brands") ?: ""
        val suggestedCategory = data.getStringExtra("suggested_category") ?: ""
        val imageUrl = data.getStringExtra("image_url") ?: ""
        val dataSource = data.getStringExtra("data_source") ?: ""
        val isOfflineData = data.getBooleanExtra("is_offline_data", false)

        val productNotFound = data.getBooleanExtra("product_not_found", false)
        val lookupFailed = data.getBooleanExtra("lookup_failed", false)
        val lookupError = data.getStringExtra("lookup_error") ?: ""

        // Store the data
        scannedBarcode = barcode
        productImageUrl = imageUrl
        isGS1Code = isGS1

        // Show barcode info
        displayBarcodeInfo(barcode, originalData, barcodeFormat)

        // Show GS1 info if available
        if (isGS1) {
            displayGS1Info(gs1ExpiryDate, gs1BatchLot, gs1SerialNumber, gs1ProductionDate, gs1BestBeforeDate, gs1GTIN)
        }

        // Load product image if available (only if user hasn't uploaded their own)
        if (imageUrl.isNotEmpty() && userUploadedImageUri == null) {
            displayImage(imageUrl, isUserUploaded = false)
        }

        // Handle different scenarios
        when {
            // GS1 with expiry date - auto-fill expiry
            isGS1 && gs1ExpiryDate.isNotEmpty() -> {
                selectedExpiryDate = gs1ExpiryDate
                textExpiryDate.text = selectedExpiryDate
                textExpiryDate.setTextColor(getColor(R.color.gray_800))

                if (productFound && productName.isNotEmpty()) {
                    // Both expiry and product info available
                    populateProductInfo(productName, brands, suggestedCategory, dataSource, isOfflineData)
                    showSuccessMessage("GS1 code scanned! Expiry date and product info loaded.")
                } else {
                    // Only expiry available
                    inputName.requestFocus()
                    showSuccessMessage("GS1 code scanned! Expiry date loaded. Please enter product name.")
                }
            }

            // GS1 without expiry date
            isGS1 -> {
                if (productFound && productName.isNotEmpty()) {
                    populateProductInfo(productName, brands, suggestedCategory, dataSource, isOfflineData)
                    showSuccessMessage("GS1 code scanned! Product info loaded. Please set expiry date.")
                } else {
                    inputName.requestFocus()
                    showInfoMessage("GS1 code scanned! Please enter product details and expiry date.")
                }
            }

            // Regular barcode with product found
            productFound && productName.isNotEmpty() -> {
                populateProductInfo(productName, brands, suggestedCategory, dataSource, isOfflineData)
                showSuccessMessage("Barcode scanned! Product information loaded.")
            }

            // Product not found
            productNotFound -> {
                inputName.requestFocus()
                showInfoMessage("Barcode scanned but product not found. Please enter details manually.")
            }

            // Lookup failed
            lookupFailed -> {
                inputName.requestFocus()
                showWarningMessage("Barcode scanned but lookup failed: $lookupError. Please enter details manually.")
            }

            // Default case
            else -> {
                inputName.requestFocus()
                showInfoMessage("Barcode scanned but unable to enter details. Please enter product details manually.")
            }
        }
    }

    private fun displayBarcodeInfo(barcode: String, originalData: String, format: String) {
        barcodeInfoLayout.visibility = View.VISIBLE
        val displayData = if (originalData != barcode && originalData.isNotEmpty()) {
            "$barcode\nOriginal: $originalData"
        } else {
            barcode
        }
        barcodeText.text = displayData
    }

    private fun displayGS1Info(expiryDate: String, batchLot: String, serialNumber: String,
                               productionDate: String, bestBeforeDate: String, gtin: String) {
        val gs1Info = mutableListOf<String>()

        if (expiryDate.isNotEmpty()) gs1Info.add("Expiry: $expiryDate")
        if (bestBeforeDate.isNotEmpty() && bestBeforeDate != expiryDate) gs1Info.add("Best Before: $bestBeforeDate")
        if (productionDate.isNotEmpty()) gs1Info.add("Production: $productionDate")
        if (batchLot.isNotEmpty()) gs1Info.add("Batch: $batchLot")
        if (serialNumber.isNotEmpty()) gs1Info.add("Serial: $serialNumber")
        if (gtin.isNotEmpty()) gs1Info.add("GTIN: $gtin")

        if (gs1Info.isNotEmpty()) {
            gs1InfoLayout.visibility = View.VISIBLE
            gs1InfoText.text = gs1Info.joinToString("\n")
        } else {
            gs1InfoLayout.visibility = View.GONE
        }
    }

    private fun populateProductInfo(productName: String, brands: String, suggestedCategory: String,
                                    dataSource: String, isOfflineData: Boolean) {
        // Populate product name
        val fullName = if (brands.isNotEmpty()) "$productName ($brands)" else productName
        inputName.setText(fullName)

        // Populate category if suggested
        if (suggestedCategory.isNotEmpty() && suggestedCategory != "Other") {
            selectedCategory = suggestedCategory
            textCategory.text = selectedCategory
            textCategory.setTextColor(getColor(R.color.gray_800))
        }

        // Show data source info if offline
        if (isOfflineData) {
            showInfoMessage("Product info loaded from cache (offline mode)")
        }
    }

    private fun showSuccessMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showInfoMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showWarningMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun clearBarcodeInfo() {
        scannedBarcode = ""
        isGS1Code = false
        barcodeInfoLayout.visibility = View.GONE
        gs1InfoLayout.visibility = View.GONE

        // Only clear product image if it came from barcode (not user uploaded)
        if (userUploadedImageUri == null) {
            productImageUrl = ""
            hideImage()
        }

        // Optionally clear the populated fields
        MaterialAlertDialogBuilder(this)
            .setTitle("Clear Scanned Data")
            .setMessage("Do you want to clear all the scanned information as well?")
            .setPositiveButton("Yes") { _, _ ->
                inputName.setText("")
                selectedCategory = ""
                textCategory.text = "Select category"
                textCategory.setTextColor(getColor(R.color.gray_400))

                // Don't clear expiry date if it came from GS1 - user might want to keep it
                if (isGS1Code) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Keep Expiry Date?")
                        .setMessage("This expiry date was extracted from the GS1 code. Do you want to keep it?")
                        .setPositiveButton("Keep") { _, _ -> }
                        .setNegativeButton("Clear") { _, _ ->
                            selectedExpiryDate = ""
                            textExpiryDate.text = "mm/dd/yyyy"
                            textExpiryDate.setTextColor(getColor(R.color.gray_400))
                        }
                        .show()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun setupInitialValues() {
        qtyText.text = quantity.toString()
        val currentDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
        textPurchaseDate.text = currentDate
        selectedPurchaseDate = currentDate
        textPurchaseDate.setTextColor(getColor(R.color.gray_800))

        // Hide info layouts initially
        barcodeInfoLayout.visibility = View.GONE
        gs1InfoLayout.visibility = View.GONE
        hideImage()

        // Set default weight unit
        textWeightUnit.text = selectedWeightUnit
    }

    private fun showCategoryDialog() {
        val categories = arrayOf("Dairy", "Meat", "Vegetables", "Fruits", "Bakery", "Frozen", "Beverages", "Cereals", "Sweets", "Other")
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Category")
            .setItems(categories) { _, which ->
                selectedCategory = categories[which]
                textCategory.text = selectedCategory
                textCategory.setTextColor(getColor(R.color.gray_800))
            }
            .show()
    }

    private fun showDatePicker(isPurchaseDate: Boolean) {
        val calendar = Calendar.getInstance()
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val date = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }.time
                val selectedDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)

                if (isPurchaseDate) {
                    selectedPurchaseDate = selectedDate
                    textPurchaseDate.text = selectedDate
                    textPurchaseDate.setTextColor(getColor(R.color.gray_800))
                } else {
                    selectedExpiryDate = selectedDate
                    textExpiryDate.text = selectedDate
                    textExpiryDate.setTextColor(getColor(R.color.gray_800))
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        if (!isPurchaseDate) datePickerDialog.datePicker.minDate = System.currentTimeMillis()
        datePickerDialog.show()
    }
    private fun saveItemToFirestore() {
        val itemName = inputName.text.toString().trim()
        if (itemName.isEmpty()) {
            Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show()
            inputName.requestFocus()
            return
        }
        if (selectedCategory.isEmpty()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedExpiryDate.isEmpty()) {
            Toast.makeText(this, "Please select expiry date", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Show loading
        showLoading(true)

        // If user uploaded an image, upload it to Cloudinary first
        if (userUploadedImageUri != null) {
            CloudinaryManager.uploadProductImage(
                context = this,
                imageUri = userUploadedImageUri!!,
                onSuccess = { cloudinaryUrl ->
                    Log.d("AddItemActivity", "Image uploaded to Cloudinary: $cloudinaryUrl")
                    // Save item with Cloudinary URL
                    saveItemWithImageUrl(cloudinaryUrl)
                },
                onError = { error ->
                    Log.e("AddItemActivity", "Cloudinary upload failed: $error")
                    showLoading(false)
                    // Show dialog asking if user wants to save without image
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Image Upload Failed")
                        .setMessage("Failed to upload image: $error\n\nDo you want to save the item without the image?")
                        .setPositiveButton("Save Without Image") { _, _ ->
                            showLoading(true)
                            saveItemWithImageUrl(productImageUrl) // Use barcode image URL if available
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            )
        } else {
            // No user-uploaded image, save directly with barcode image URL if available
            saveItemWithImageUrl(productImageUrl)
        }
    }

    private fun saveItemWithImageUrl(imageUrl: String) {
        val itemName = inputName.text.toString().trim()
        val currentUser = auth.currentUser ?: return

        // Get optional fields as strings
        val amount = inputAmount.text.toString().trim()
        val weight = inputWeight.text.toString().trim()
        val notes = inputNotes.text.toString().trim()

        val daysLeft = calculateDaysLeft(selectedExpiryDate)
        val status = determineStatus(daysLeft)
        val now = Date()

        Log.d("AddItemActivity", "Saving item for userId: ${currentUser.uid}")

        val groceryItem = GroceryItem(
            name = itemName,
            category = selectedCategory,
            expiryDate = selectedExpiryDate,
            purchaseDate = selectedPurchaseDate,
            quantity = quantity,
            amount = amount,
            weight = weight,
            weightUnit = if (weight.isNotEmpty()) selectedWeightUnit else "",
            storageLocation = selectedStorageLocation,
            notes = notes,
            status = status,
            daysLeft = daysLeft,
            barcode = scannedBarcode,
            imageUrl = imageUrl,
            isGS1 = isGS1Code,
            createdAt = now,
            updatedAt = now
        )

        lifecycleScope.launch {
            try {
                val result = firestoreRepository.addGroceryItem(groceryItem)
                showLoading(false)
                if (result.isSuccess) {
                    Log.d("AddItemActivity", "Item saved successfully: $itemName")
                    Toast.makeText(this@AddItemActivity, "Item saved successfully!", Toast.LENGTH_SHORT).show()
                    setResult(RESULT_OK)
                    finish()
                } else {
                    val error = result.exceptionOrNull()
                    Log.e("AddItemActivity", "Failed to save item: ${error?.message}", error)
                    Toast.makeText(this@AddItemActivity, "Failed to save item: ${error?.message}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                showLoading(false)
                Log.e("AddItemActivity", "Exception while saving item: ${e.message}", e)
                Toast.makeText(this@AddItemActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        saveButton.isEnabled = !show
        saveButton.alpha = if (show) 0.6f else 1f
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
            val daysLeft = TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()

            Log.d("AddItemActivity", "Expiry: $expiryDate, Days left: $daysLeft")
            return daysLeft
        } catch (e: Exception) {
            Log.e("AddItemActivity", "Error calculating days left: ${e.message}", e)
            0
        }
    }

    private fun determineStatus(daysLeft: Int): String {
        return when {
            daysLeft < 0 -> {
                Log.d("AddItemActivity", "Status: expired (days: $daysLeft)")
                "expired"
            }
            daysLeft == 0 -> {
                Log.d("AddItemActivity", "Status: expiring (days: $daysLeft)")
                "expiring"
            }
            daysLeft <= 3 -> {
                Log.d("AddItemActivity", "Status: expiring (days: $daysLeft)")
                "expiring"
            }
            else -> {
                Log.d("AddItemActivity", "Status: fresh (days: $daysLeft)")
                "fresh"
            }
        }
    }

    private fun setupBackPressedHandler() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasUnsavedChanges()) showDiscardChangesDialog() else finish()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun hasUnsavedChanges(): Boolean {
        return inputName.text.toString().trim().isNotEmpty() ||
                selectedCategory.isNotEmpty() ||
                selectedExpiryDate.isNotEmpty() ||
                quantity != 1 ||
                scannedBarcode.isNotEmpty() ||
                userUploadedImageUri != null ||
                inputAmount.text.toString().trim().isNotEmpty() ||
                inputWeight.text.toString().trim().isNotEmpty() ||
                selectedStorageLocation.isNotEmpty() ||
                inputNotes.text.toString().trim().isNotEmpty()
    }

    private fun showDiscardChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Discard Changes")
            .setMessage("You have unsaved changes. Are you sure you want to discard them?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}