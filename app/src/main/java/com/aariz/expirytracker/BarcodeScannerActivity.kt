package com.aariz.expirytracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BarcodeScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var productCacheRepository: ProductCacheRepository
    private lateinit var gs1Parser: GS1Parser

    private var imageCapture: ImageCapture? = null
    private var isProcessing = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required for barcode scanning", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_barcode_scanner)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Apply window insets to header and bottom bar
        findViewById<View>(R.id.header_section).applyHeaderInsets()
        findViewById<View>(R.id.bottom_bar).applyBottomNavInsets()

        previewView = findViewById(R.id.preview_view)
        productCacheRepository = ProductCacheRepository(this)
        gs1Parser = GS1Parser()

        // Initialize barcode scanner with comprehensive format support
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                // Standard barcodes
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_ITF,

                // 2D codes (GS1 compatible)
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC
            )
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupButtons()

        // Request camera permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupButtons() {
        findViewById<MaterialButton>(R.id.btn_back).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_manual_entry).setOnClickListener {
            // Option for manual barcode entry
            showManualEntryDialog()
        }
    }

    private fun showManualEntryDialog() {
        val builder = android.app.AlertDialog.Builder(this)
        val editText = android.widget.EditText(this)
        editText.hint = "Enter barcode/GS1 data manually"
        editText.inputType = android.text.InputType.TYPE_CLASS_TEXT

        builder.setTitle("Manual Entry")
            .setMessage("Enter barcode number or GS1 data")
            .setView(editText)
            .setPositiveButton("Process") { _, _ ->
                val input = editText.text.toString().trim()
                if (input.isNotEmpty()) {
                    processScannedData(input, "MANUAL_ENTRY")
                } else {
                    Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { rawData, format ->
                        if (!isProcessing) {
                            isProcessing = true
                            processScannedData(rawData, format)
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processScannedData(rawData: String, format: String) {
        Log.d(TAG, "Processing scanned data: $rawData (format: $format)")

        lifecycleScope.launch {
            try {
                // Show loading state
                runOnUiThread {
                    Toast.makeText(this@BarcodeScannerActivity, "Processing barcode...", Toast.LENGTH_SHORT).show()
                }

                // Check if this is GS1 format
                val isGS1 = gs1Parser.isGS1Format(rawData)
                Log.d(TAG, "Is GS1 format: $isGS1")

                var gs1Data: GS1ParsedData? = null
                var primaryBarcode = rawData

                if (isGS1) {
                    // Parse GS1 data
                    gs1Data = gs1Parser.parseGS1Data(rawData)
                    primaryBarcode = gs1Parser.extractPrimaryBarcode(gs1Data)
                    Log.d(TAG, "GS1 parsed data: $gs1Data")
                    Log.d(TAG, "Primary barcode extracted: $primaryBarcode")
                }

                // Look up product info using primary barcode
                val productResult = if (primaryBarcode.isNotEmpty() && primaryBarcode.length >= 8) {
                    productCacheRepository.getProductInfo(primaryBarcode)
                } else {
                    // Skip product lookup for invalid barcodes
                    Result.success(ProductLookupResult(null, DataSource.NETWORK_FAILED, false))
                }

                runOnUiThread {
                    handleProcessingResult(rawData, primaryBarcode, gs1Data, productResult, format)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing scanned data", e)
                runOnUiThread {
                    Toast.makeText(
                        this@BarcodeScannerActivity,
                        "Error processing barcode: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    isProcessing = false
                }
            }
        }
    }

    private fun handleProcessingResult(
        originalData: String,
        primaryBarcode: String,
        gs1Data: GS1ParsedData?,
        productResult: Result<ProductLookupResult>,
        format: String
    ) {
        val resultIntent = android.content.Intent().apply {
            // Basic barcode info
            putExtra("barcode", primaryBarcode)
            putExtra("original_data", originalData)
            putExtra("barcode_format", format)

            // GS1 specific data
            putExtra("is_gs1", gs1Data != null)
            gs1Data?.let { gs1 ->
                putExtra("gs1_expiry_date", gs1.expiryDate)
                putExtra("gs1_batch_lot", gs1.batchLot)
                putExtra("gs1_serial_number", gs1.serialNumber)
                putExtra("gs1_production_date", gs1.productionDate)
                putExtra("gs1_best_before_date", gs1.bestBeforeDate)
                putExtra("gs1_gtin", gs1.gtin)
            }

            // Product lookup results
            if (productResult.isSuccess) {
                val lookupResult = productResult.getOrNull()
                val productInfo = lookupResult?.productInfo

                if (productInfo != null) {
                    putExtra("product_name", productInfo.productName)
                    putExtra("brands", productInfo.brands)
                    putExtra("suggested_category", productInfo.suggestedCategory)
                    putExtra("image_url", productInfo.imageUrl)
                    putExtra("product_found", true)
                    putExtra("data_source", lookupResult.source.name)
                    putExtra("is_offline_data", lookupResult.isOfflineData)
                } else {
                    putExtra("product_found", false)
                    putExtra("product_not_found", true)
                }
            } else {
                putExtra("product_found", false)
                putExtra("lookup_failed", true)
                putExtra("lookup_error", productResult.exceptionOrNull()?.message ?: "Unknown error")
            }
        }

        // Show appropriate message based on what we found
        val message = when {
            gs1Data?.expiryDate?.isNotEmpty() == true && productResult.isSuccess && productResult.getOrNull()?.productInfo != null -> {
                "Barcode scanned! Product info and expiry date loaded."
            }
            gs1Data?.expiryDate?.isNotEmpty() == true -> {
                "Barcode scanned! Expiry date found: ${gs1Data.expiryDate}"
            }
            productResult.isSuccess && productResult.getOrNull()?.productInfo != null -> {
                "Barcode scanned! Product information loaded."
            }
            else -> {
                "Barcode scanned! Please enter product details manually."
            }
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private inner class BarcodeAnalyzer(private val barcodeListener: (String, String) -> Unit) :
        ImageAnalysis.Analyzer {

        @OptIn(ExperimentalGetImage::class)
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                barcodeScanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        for (barcode in barcodes) {
                            barcode.rawValue?.let { value ->
                                val format = when (barcode.format) {
                                    Barcode.FORMAT_EAN_13 -> "EAN_13"
                                    Barcode.FORMAT_EAN_8 -> "EAN_8"
                                    Barcode.FORMAT_UPC_A -> "UPC_A"
                                    Barcode.FORMAT_UPC_E -> "UPC_E"
                                    Barcode.FORMAT_CODE_128 -> "CODE_128"
                                    Barcode.FORMAT_CODE_39 -> "CODE_39"
                                    Barcode.FORMAT_CODE_93 -> "CODE_93"
                                    Barcode.FORMAT_CODABAR -> "CODABAR"
                                    Barcode.FORMAT_ITF -> "ITF"
                                    Barcode.FORMAT_QR_CODE -> "QR_CODE"
                                    Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
                                    Barcode.FORMAT_PDF417 -> "PDF417"
                                    Barcode.FORMAT_AZTEC -> "AZTEC"
                                    else -> "UNKNOWN"
                                }
                                Log.d(TAG, "Detected barcode: $value (format: $format)")
                                barcodeListener(value, format)
                                return@addOnSuccessListener
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }

    companion object {
        private const val TAG = "BarcodeScannerActivity"
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}