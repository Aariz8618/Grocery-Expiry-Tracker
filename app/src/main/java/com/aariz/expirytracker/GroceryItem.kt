package com.aariz.expirytracker

import com.google.firebase.firestore.DocumentId
import java.util.Date

data class GroceryItem(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val expiryDate: String = "",
    val purchaseDate: String = "",
    val quantity: Int = 1,
    var status: String = "fresh", // fresh, expiring, expired
    var daysLeft: Int = 0,
    val barcode: String = "", // Store barcode if scanned
    val imageUrl: String = "", // Store product image URL
    val isGS1: Boolean = false, // Track if this was from GS1 code
    val batchLot: String = "", // GS1 batch/lot number
    val serialNumber: String = "", // GS1 serial number
    val weight: String = "", // Weight value from input_weight
    val weightUnit: String = "", // Weight unit (kg, g, lb, oz)
    val amount: String = "", // Price/amount from input_amount (renamed from price)
    val storageLocation: String = "", // Storage location (Refrigerator, Freezer, Pantry, etc.)
    val store: String = "", // Store name (keeping for backward compatibility)
    val notes: String = "", // User notes from input_notes
    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        name = "",
        category = "",
        expiryDate = "",
        purchaseDate = "",
        quantity = 1,
        status = "fresh",
        daysLeft = 0,
        barcode = "",
        imageUrl = "",
        isGS1 = false,
        batchLot = "",
        serialNumber = "",
        weight = "",
        weightUnit = "",
        amount = "",
        storageLocation = "",
        store = "",
        notes = "",
        createdAt = Date(),
        updatedAt = Date()
    )
}