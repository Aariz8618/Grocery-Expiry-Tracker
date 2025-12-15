package com.aariz.expirytracker

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Create or update user profile
    suspend fun createUserProfile(user: User): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .set(user.copy(id = currentUser.uid))
                .await()

            Log.d("FirestoreRepository", "User profile created/updated successfully")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error creating user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Add grocery item to user's subcollection
    suspend fun addGroceryItem(item: GroceryItem): Result<String> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d("FirestoreRepository", "Attempting to save item: ${item.name} for userId: ${currentUser.uid}")

            // Reference to the grocery_items subcollection
            val docRef = firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document() // Auto-generated ID

            val data = item.copy(id = docRef.id)
            docRef.set(data).await()

            Log.d("FirestoreRepository", "Item saved successfully with id: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error saving item: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Get user's grocery items from subcollection
    suspend fun getUserGroceryItems(): Result<List<GroceryItem>> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            Log.d("FirestoreRepository", "Fetching items for userId: ${currentUser.uid}")

            val snapshot = firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .get()
                .await()

            val items = snapshot.toObjects(GroceryItem::class.java)
            Log.d("FirestoreRepository", "Fetched ${items.size} items for userId: ${currentUser.uid}")
            Result.success(items)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching items: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Update grocery item
    suspend fun updateGroceryItem(item: GroceryItem): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document(item.id)
                .set(item)
                .await()

            Log.d("FirestoreRepository", "Item updated successfully: ${item.name}")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error updating item: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Delete grocery item
    suspend fun deleteGroceryItem(itemId: String): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document(itemId)
                .delete()
                .await()

            Log.d("FirestoreRepository", "Item deleted successfully: $itemId")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error deleting item: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Get user profile
    suspend fun getUserProfile(): Result<User?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            val snapshot = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            val user = snapshot.toObject(User::class.java)
            Log.d("FirestoreRepository", "User profile fetched successfully")
            Result.success(user)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error fetching user profile: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Update item category
    suspend fun updateItemCategory(itemId: String, newCategory: String): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document(itemId)
                .update("category", newCategory)
                .await()

            Log.d("FirestoreRepository", "Item category updated successfully: $itemId")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error updating item category: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Mark item as used
    suspend fun markItemAsUsed(itemId: String): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document(itemId)
                .update("status", "used")
                .await()

            Log.d("FirestoreRepository", "Item marked as used: $itemId")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error marking item as used: ${e.message}", e)
            Result.failure(e)
        }
    }

    // Delete item
    suspend fun deleteItem(itemId: String): Result<Void?> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                Log.e("FirestoreRepository", "User not authenticated")
                return Result.failure(Exception("User not authenticated"))
            }

            firestore.collection("users")
                .document(currentUser.uid)
                .collection("grocery_items")
                .document(itemId)
                .delete()
                .await()

            Log.d("FirestoreRepository", "Item deleted successfully: $itemId")
            Result.success(null)
        } catch (e: Exception) {
            Log.e("FirestoreRepository", "Error deleting item: ${e.message}", e)
            Result.failure(e)
        }
    }
}