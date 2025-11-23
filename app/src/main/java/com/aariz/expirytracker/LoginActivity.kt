package com.aariz.expirytracker

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var oneTapClient: SignInClient
    private lateinit var signInRequest: BeginSignInRequest
    private lateinit var firestoreRepository: FirestoreRepository
    private var isPasswordVisible = false

    // Register for activity result - One Tap Sign-In
    private val oneTapSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        try {
            val credential = oneTapClient.getSignInCredentialFromIntent(result.data)
            val idToken = credential.googleIdToken
            when {
                idToken != null -> {
                    firebaseAuthWithGoogle(idToken)
                }
                else -> {
                    Log.d("LoginAuth", "No ID token!")
                    Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: ApiException) {
            Log.w("LoginAuth", "One Tap sign in failed", e)
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_login)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        // Initialize One Tap Sign-In client
        oneTapClient = Identity.getSignInClient(this)

        // Configure One Tap Sign-In request
        signInRequest = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .setAutoSelectEnabled(false)
            .build()

        val emailField = findViewById<EditText>(R.id.input_email)
        val passwordField = findViewById<EditText>(R.id.input_password)
        val loginButton = findViewById<TextView>(R.id.button_login)
        val forgotPassword = findViewById<TextView>(R.id.text_forgot_password)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.toggle_password_visibility)
        val googleButton = findViewById<LinearLayout>(R.id.button_google)
        val signupLink = findViewById<TextView>(R.id.text_signup)

        // Navigation to Sign Up
        signupLink.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        // Toggle password visibility
        togglePasswordVisibility.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_on)
            } else {
                passwordField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye_off)
            }
            passwordField.setSelection(passwordField.text.length)
        }

        // Login Button
        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Disable button and show loading state
            loginButton.isEnabled = false
            loginButton.text = "Signing In..."
            loginButton.alpha = 0.6f

            signInWithEmail(email, password)
        }

        // Forgot Password
        forgotPassword.setOnClickListener {
            val email = emailField.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email address first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendPasswordResetEmail(email)
        }

        // Google Sign In with One Tap
        googleButton.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    oneTapSignInLauncher.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e("LoginAuth", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    Toast.makeText(this, "Google Sign-In failed to start", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener(this) { e ->
                Log.d("LoginAuth", "No Google accounts found: ${e.localizedMessage}")
                Toast.makeText(this, "No Google accounts found. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginAuth", "signInWithCredential:success")
                    val user = auth.currentUser
                    if (user != null) {
                        val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false
                        if (isNewUser) {
                            val nameParts = user.displayName?.split(" ") ?: listOf("", "")
                            val firstName = nameParts.firstOrNull() ?: ""
                            val lastName = nameParts.drop(1).joinToString(" ")

                            val userProfile = User(
                                id = user.uid,
                                firstName = firstName,
                                lastName = lastName,
                                email = user.email ?: "",
                                dateOfBirth = "",
                                createdAt = System.currentTimeMillis()
                            )

                            CoroutineScope(Dispatchers.IO).launch {
                                val result = firestoreRepository.createUserProfile(userProfile)
                                withContext(Dispatchers.Main) {
                                    if (result.isSuccess) {
                                        Log.d("LoginAuth", "User profile created in Firestore")
                                    } else {
                                        Log.e("LoginAuth", "Failed to create profile: ${result.exceptionOrNull()?.message}")
                                    }
                                    proceedToApp()
                                }
                            }
                        } else {
                            proceedToApp()
                        }
                    }
                } else {
                    Log.w("LoginAuth", "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun proceedToApp() {
        AuthHelper.markUserLoggedIn(this)
        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginAuth", "signInWithEmail:success")
                    val user = auth.currentUser

                    if (user?.isEmailVerified == true) {
                        AuthHelper.markUserLoggedIn(this)
                        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, DashboardActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(
                            this,
                            "Please verify your email first. Check your inbox for verification link.",
                            Toast.LENGTH_LONG
                        ).show()
                        auth.signOut()
                        showResendVerificationDialog(email, password)
                    }
                } else {
                    Log.w("LoginAuth", "signInWithEmail:failure", task.exception)
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ||
                                task.exception?.message?.contains("INVALID_LOGIN_CREDENTIALS") == true ->
                            "Invalid email or password. Please try again."
                        task.exception?.message?.contains("password is invalid") == true ||
                                task.exception?.message?.contains("wrong password") == true ->
                            "Incorrect password. Please try again."
                        task.exception?.message?.contains("email address is badly formatted") == true ->
                            "Please enter a valid email address"
                        task.exception?.message?.contains("too many requests") == true ->
                            "Too many failed attempts. Please try again later."
                        task.exception?.message?.contains("network error") == true ->
                            "Network error. Please check your internet connection."
                        else -> "Login failed: ${task.exception?.message}"
                    }

                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }

                // Re-enable login button
                findViewById<TextView>(R.id.button_login).apply {
                    isEnabled = true
                    text = "Log In"
                    alpha = 1f
                }
            }
    }

    private fun showResendVerificationDialog(email: String, password: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Email Not Verified")
            .setMessage("Would you like us to resend the verification email?")
            .setPositiveButton("Resend") { _, _ ->
                resendEmailVerification(email, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset email sent to $email. Check your inbox.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val errorMessage = when {
                        task.exception?.message?.contains("no user record") == true ->
                            "No account found with this email address"
                        else -> "Failed to send reset email: ${task.exception?.message}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun resendEmailVerification(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { emailTask ->
                            if (emailTask.isSuccessful) {
                                Toast.makeText(
                                    this,
                                    "Verification email resent to $email. Check your inbox and spam folder.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to resend verification email: ${emailTask.exception?.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    auth.signOut()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to authenticate. Please check your password.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}