package com.aariz.expirytracker

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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

class SignupActivity : AppCompatActivity() {

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
                    Log.d("SignupAuth", "No ID token!")
                    Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: ApiException) {
            Log.w("SignupAuth", "One Tap sign in failed", e)
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_signup)

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

        val firstNameField = findViewById<EditText>(R.id.input_first_name)
        val lastNameField = findViewById<EditText>(R.id.input_last_name)
        val emailField = findViewById<EditText>(R.id.input_email)
        val passwordField = findViewById<EditText>(R.id.input_password)
        val registerButton = findViewById<TextView>(R.id.button_register)
        val togglePasswordVisibility = findViewById<ImageView>(R.id.toggle_password_visibility)
        val googleButton = findViewById<FrameLayout>(R.id.button_google)
        val facebookButton = findViewById<FrameLayout>(R.id.button_facebook)
        val signinLink = findViewById<TextView>(R.id.text_signin)

        // Navigation to Sign In
        signinLink.setOnClickListener {
            finish()
        }

        // Password visibility toggle
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

        // Register Button
        registerButton.setOnClickListener {
            val firstName = firstNameField.text.toString().trim()
            val lastName = lastNameField.text.toString().trim()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            // Validation
            if (firstName.isEmpty() || lastName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isValidEmail(email)) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            showEmailGuidanceDialog(firstName, lastName, email, password)
        }

        // Google Sign In with One Tap
        googleButton.setOnClickListener {
            signInWithGoogle()
        }

        // Facebook button - Coming Soon
        facebookButton.setOnClickListener {
            Toast.makeText(this, "Facebook login coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithGoogle() {
        oneTapClient.beginSignIn(signInRequest)
            .addOnSuccessListener(this) { result ->
                try {
                    val intentSenderRequest = IntentSenderRequest.Builder(result.pendingIntent.intentSender).build()
                    oneTapSignInLauncher.launch(intentSenderRequest)
                } catch (e: IntentSender.SendIntentException) {
                    Log.e("SignupAuth", "Couldn't start One Tap UI: ${e.localizedMessage}")
                    Toast.makeText(this, "Google Sign-In failed to start", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener(this) { e ->
                Log.d("SignupAuth", "No Google accounts found: ${e.localizedMessage}")
                Toast.makeText(this, "No Google accounts found. Please try again.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SignupAuth", "signInWithCredential:success")
                    val user = auth.currentUser
                    if (user != null) {
                        val isNewUser = task.result?.additionalUserInfo?.isNewUser ?: false

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
                                    Log.d("SignupAuth", "User profile created/updated in Firestore")
                                } else {
                                    Log.e("SignupAuth", "Failed to create profile: ${result.exceptionOrNull()?.message}")
                                }
                                proceedToApp(isNewUser)
                            }
                        }
                    }
                } else {
                    Log.w("SignupAuth", "signInWithCredential:failure", task.exception)
                    Toast.makeText(
                        this,
                        "Authentication failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    private fun proceedToApp(isNewUser: Boolean) {
        AuthHelper.markUserLoggedIn(this)
        val message = if (isNewUser) "Account created successfully!" else "Welcome back!"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }

    private fun showEmailGuidanceDialog(firstName: String, lastName: String, email: String, password: String) {
        AlertDialog.Builder(this)
            .setTitle("Email Verification Required")
            .setMessage(
                """Before creating your account, please note:
                
• A verification email will be sent to: $email
• Check your SPAM/Junk folder if not in inbox
• Gmail users: Also check Promotions tab
• The email will come from: support@freshtrack-d3269.firebaseapp.com

The verification email may take 2-5 minutes to arrive."""
            )
            .setPositiveButton("Continue & Create Account") { _, _ ->
                proceedWithAccountCreation(firstName, lastName, email, password)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun proceedWithAccountCreation(firstName: String, lastName: String, email: String, password: String) {
        findViewById<TextView>(R.id.button_register).apply {
            isEnabled = false
            isClickable = false
            alpha = 0.6f
        }

        createAccountWithEmail(firstName, lastName, email, password)
    }

    private fun createAccountWithEmail(firstName: String, lastName: String, email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("SignupAuth", "createUserWithEmail:success")
                    val firebaseUser = auth.currentUser
                    if (firebaseUser != null) {
                        val user = User(
                            id = firebaseUser.uid,
                            firstName = firstName,
                            lastName = lastName,
                            email = email,
                            dateOfBirth = "",
                            createdAt = System.currentTimeMillis()
                        )

                        CoroutineScope(Dispatchers.IO).launch {
                            val result = firestoreRepository.createUserProfile(user)
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    Log.d("SignupAuth", "User profile saved to Firestore")
                                } else {
                                    Log.e("SignupAuth", "Failed to save user profile: ${result.exceptionOrNull()?.message}")
                                }
                                sendVerificationEmail(firebaseUser, email)
                            }
                        }
                    } else {
                        showError("Account creation failed. Please try again.")
                    }
                } else {
                    Log.w("SignupAuth", "createUserWithEmail:failure", task.exception)
                    val errorMessage = when {
                        task.exception?.message?.contains("email address is already in use") == true ->
                            "This email is already registered. Please login instead."
                        task.exception?.message?.contains("email address is badly formatted") == true ->
                            "Please enter a valid email address"
                        task.exception?.message?.contains("weak password") == true ->
                            "Password is too weak. Please use a stronger password"
                        task.exception?.message?.contains("network error") == true ->
                            "Network error. Please check your internet connection."
                        else -> "Registration failed: ${task.exception?.message}"
                    }

                    showError(errorMessage)
                }
            }
    }

    private fun sendVerificationEmail(user: com.google.firebase.auth.FirebaseUser, email: String) {
        user.sendEmailVerification()
            .addOnCompleteListener { emailTask ->
                if (emailTask.isSuccessful) {
                    Log.d("SignupAuth", "Email verification sent successfully")
                    showEmailSentDialog(email)
                    auth.signOut()
                } else {
                    Log.w("SignupAuth", "sendEmailVerification failed", emailTask.exception)
                    AlertDialog.Builder(this)
                        .setTitle("Account Created")
                        .setMessage("Your account was created but the verification email failed to send. You can try resending it from the login screen.")
                        .setPositiveButton("Go to Login") { _, _ ->
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                }
            }
    }

    private fun showEmailSentDialog(email: String) {
        AlertDialog.Builder(this)
            .setTitle("Account Created Successfully!")
            .setMessage(
                """Verification email sent to:
$email

WHERE TO LOOK:
• Check your Inbox first
• Then check SPAM/Junk folder
• Gmail: Check Promotions tab or Spam Folder
• Outlook: Check Junk folder
• Yahoo: Check Spam folder

TIMING:
Email may take 2-5 minutes to arrive

SENDER:
From: support@freshtrack-d3269.firebaseapp.com"""
            )
            .setPositiveButton("Got It!") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        findViewById<TextView>(R.id.button_register).apply {
            isEnabled = true
            isClickable = true
            alpha = 1f
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}
