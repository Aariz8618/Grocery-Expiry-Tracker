package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.viewpager2.widget.ViewPager2
import android.widget.TextView
import com.aariz.expirytracker.adapters.OnboardingAdapter
import com.aariz.expirytracker.models.OnboardingItem

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var btnNext: CardView

    private lateinit var btnSkip: TextView
    private lateinit var dot1: View
    private lateinit var dot2: View
    private lateinit var dot3: View

    private val onboardingItems = listOf(
        OnboardingItem(
            image = R.drawable.mascot_waving,
            title = "Welcome to FreshTrack!",
            description = "Your friendly companion for keeping food fresh."
        ),
        OnboardingItem(
            image = R.drawable.mascot_apple,
            title = "Log Your Groceries in Seconds",
            description = "Add items effortlessly — your kitchen stays organized."
        ),
        OnboardingItem(
            image = R.drawable.mascot_clock,
            title = "Never Miss an Expiry Date",
            description = "Get timely alerts before your food goes bad."
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
                viewPager.currentItem += 1
            } else {
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
        // Find the TextView inside the CardView
        val textView = btnNext.findViewById<TextView>(R.id.button_next_text)
        if (position == onboardingItems.size - 1) {
            textView?.text = "Get Started"
        } else {
            textView?.text = "Next"
        }
    }

    private fun finishOnboarding() {
        val sharedPreferences = getSharedPreferences("AppPreferences", MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }
}
