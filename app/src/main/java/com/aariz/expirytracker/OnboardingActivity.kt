package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.aariz.expirytracker.adapters.OnboardingAdapter
import com.aariz.expirytracker.models.OnboardingItem

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: TextView
    private lateinit var btnSkip: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    private val onboardingItems = listOf(
        OnboardingItem(
            image = R.drawable.ic_onboarding_track,
            title = "Track Your Groceries",
            description = "Never let your food go to waste. Track expiry dates and get timely reminders for all your grocery items."
        ),
        OnboardingItem(
            image = R.drawable.ic_onboarding_reminder,
            title = "Smart Reminders",
            description = "Get notified before your items expire. Set custom reminders and never miss a deadline again."
        ),
        OnboardingItem(
            image = R.drawable.ic_onboarding_save,
            title = "Save Money & Food",
            description = "Reduce food waste and save money by using items before they expire. Make your groceries last longer."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        initViews()
        setupViewPager()
        setupClickListeners()
    }

    private fun initViews() {
        viewPager = findViewById(R.id.viewpager_onboarding)
        btnNext = findViewById(R.id.button_next)
        btnSkip = findViewById(R.id.text_skip)
        dot1 = findViewById(R.id.dot_1)
        dot2 = findViewById(R.id.dot_2)
        dot3 = findViewById(R.id.dot_3)
    }

    private fun setupViewPager() {
        val adapter = OnboardingAdapter(onboardingItems)
        viewPager.adapter = adapter

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateDots(position)
                updateButton(position)
            }
        })
    }

    private fun setupClickListeners() {
        btnNext.setOnClickListener {
            if (viewPager.currentItem < onboardingItems.size - 1) {
                // Go to next page
                viewPager.currentItem += 1
            } else {
                // Complete onboarding
                finishOnboarding()
            }
        }

        btnSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updateDots(position: Int) {
        val activeDrawable = R.drawable.bg_dot_active
        val inactiveDrawable = R.drawable.bg_dot_inactive

        when (position) {
            0 -> {
                dot1.setBackgroundResource(activeDrawable)
                dot2.setBackgroundResource(inactiveDrawable)
                dot3.setBackgroundResource(inactiveDrawable)
            }
            1 -> {
                dot1.setBackgroundResource(inactiveDrawable)
                dot2.setBackgroundResource(activeDrawable)
                dot3.setBackgroundResource(inactiveDrawable)
            }
            2 -> {
                dot1.setBackgroundResource(inactiveDrawable)
                dot2.setBackgroundResource(inactiveDrawable)
                dot3.setBackgroundResource(activeDrawable)
            }
        }
    }

    private fun updateButton(position: Int) {
        if (position == onboardingItems.size - 1) {
            btnNext.text = "Get Started"
        } else {
            btnNext.text = "Next"
        }
    }

    private fun finishOnboarding() {
        // Save onboarding completion status
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()

        // Navigate to Login/Signup Activity
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}