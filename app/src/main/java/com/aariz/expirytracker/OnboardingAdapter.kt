package com.aariz.expirytracker.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aariz.expirytracker.R
import com.aariz.expirytracker.models.OnboardingItem

class OnboardingAdapter(private val items: List<OnboardingItem>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    inner class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.onboarding_image)
        val title: TextView = view.findViewById(R.id.onboarding_title)
        val description: TextView = view.findViewById(R.id.onboarding_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_onboarding_page, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val item = items[position]
        holder.image.setImageResource(item.image)
        holder.title.text = item.title
        holder.description.text = item.description

        // Add entrance animation
        animateMascotEntrance(holder.image)

        // Add continuous pulse animation
        animateMascotPulse(holder.image)

        // Animate text elements
        animateText(holder.title, 200)
        animateText(holder.description, 400)
    }

    override fun getItemCount(): Int = items.size

    // Scale & Fade entrance animation
    private fun animateMascotEntrance(imageView: ImageView) {
        // Scale animations
        val scaleX = ObjectAnimator.ofFloat(imageView, "scaleX", 0.7f, 1f)
        val scaleY = ObjectAnimator.ofFloat(imageView, "scaleY", 0.7f, 1f)

        // Fade in animation
        val fadeIn = ObjectAnimator.ofFloat(imageView, "alpha", 0f, 1f)

        // Slight bounce from top
        val translateY = ObjectAnimator.ofFloat(imageView, "translationY", -50f, 0f)

        // Combine all animations
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY, fadeIn, translateY)
        animatorSet.duration = 700
        animatorSet.interpolator = AccelerateDecelerateInterpolator()
        animatorSet.start()
    }

    // Subtle continuous pulse animation
    // Subtle continuous pulse animation
    private fun animateMascotPulse(imageView: ImageView) {
        val pulseX = ObjectAnimator.ofFloat(imageView, "scaleX", 1f, 1.05f, 1f)
        val pulseY = ObjectAnimator.ofFloat(imageView, "scaleY", 1f, 1.05f, 1f)

        // Set repeat properties individually
        pulseX.repeatCount = ObjectAnimator.INFINITE
        pulseX.repeatMode = ObjectAnimator.RESTART

        pulseY.repeatCount = ObjectAnimator.INFINITE
        pulseY.repeatMode = ObjectAnimator.RESTART

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(pulseX, pulseY)
        animatorSet.duration = 2000
        animatorSet.startDelay = 800 // Wait for entrance animation to finish
        animatorSet.start()
    }


    // Animate text with fade and slide up
    private fun animateText(textView: TextView, delay: Long) {
        textView.alpha = 0f
        textView.translationY = 30f

        textView.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(600)
            .setStartDelay(delay)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }
}
