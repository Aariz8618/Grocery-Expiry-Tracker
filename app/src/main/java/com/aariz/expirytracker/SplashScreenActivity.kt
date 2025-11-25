package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class SplashScreenActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private val splashDelay = 3500L // 3.5 seconds

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_splash)

        auth = FirebaseAuth.getInstance()

        // Check if this is a fresh install by looking at shared preferences
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstRun = prefs.getBoolean("is_first_run", true)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (isFirstRun) {
            auth.signOut()
            prefs.edit().putBoolean("is_first_run", false).apply()
            Log.d("SplashScreenActivity", "First run detected - clearing auth state")
        } else if (!hasUserLoggedInBefore) {
            auth.signOut()
            Log.d("SplashScreenActivity", "No previous successful login - clearing auth state")
        }

        // Delay for splash screen, then check navigation
        Handler(Looper.getMainLooper()).postDelayed({
            navigateToNextScreen()
        }, splashDelay)
    }

    private fun navigateToNextScreen() {
        // Check onboarding status first
        val onboardingPrefs = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        val hasCompletedOnboarding = onboardingPrefs.getBoolean("onboarding_completed", false)

        if (!hasCompletedOnboarding) {
            // First time user - show onboarding
            Log.d("SplashScreenActivity", "First time user - navigating to onboarding")
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        // User has completed onboarding - check authentication
        val currentUser = auth.currentUser
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasUserLoggedInBefore = prefs.getBoolean("user_logged_in_before", false)

        if (currentUser != null && currentUser.isEmailVerified && hasUserLoggedInBefore) {
            Log.d("SplashScreenActivity", "User authenticated: ${currentUser.email}")
            startActivity(Intent(this, DashboardActivity::class.java))
        } else {
            if (currentUser != null && !hasUserLoggedInBefore) {
                auth.signOut()
            }
            Log.d("SplashScreenActivity", "No authenticated user found - going to login")
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

    fun markUserLoggedIn() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", true).apply()
    }

    private fun resetFirstRunFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_first_run", true)
            .putBoolean("user_logged_in_before", false)
            .apply()
    }
}
