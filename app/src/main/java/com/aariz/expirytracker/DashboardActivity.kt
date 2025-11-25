package com.aariz.expirytracker

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class DashboardActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_container)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        auth = FirebaseAuth.getInstance()

        // Check authentication
        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        initViews()

        // Load home fragment by default
        if (savedInstanceState == null) {
            loadFragment(HomeFragment())
        }
    }

    private fun initViews() {
        bottomNav = findViewById(R.id.bottom_navigation)
        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    loadFragment(HomeFragment())
                    true
                }
                R.id.nav_stats -> {
                    loadFragment(StatisticsFragment())
                    true
                }
                R.id.nav_recipes -> {
                    loadFragment(RecipesFragment())
                    true
                }
                R.id.nav_settings -> {
                    loadFragment(SettingsFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    override fun onResume() {
        super.onResume()

        if (auth.currentUser == null) {
            clearUserLoggedInFlag()
            startActivity(android.content.Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun clearUserLoggedInFlag() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        prefs.edit().putBoolean("user_logged_in_before", false).apply()
    }
}