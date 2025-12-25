package com.aariz.expirytracker

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2

class StatisticsFragment : Fragment() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var buttonExport: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Apply header insets
        view.findViewById<View>(R.id.header_section)?.applyHeaderInsets()

        initViews(view)
        setupViewPager()
        setupExportButton()
    }

    private fun initViews(view: View) {
        viewPager = view.findViewById(R.id.view_pager)
        tabLayout = view.findViewById(R.id.tab_layout)
        buttonExport = view.findViewById(R.id.button_export)
    }

    private fun setupViewPager() {
        val adapter = StatsPagerAdapter(this)
        viewPager.adapter = adapter

        // Connect TabLayout with ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Money Savings"
                1 -> "Usage Stats"
                else -> null
            }
        }.attach()
    }

    private fun setupExportButton() {
        buttonExport.setOnClickListener {
            // Get the current fragment
            val currentFragment = childFragmentManager.findFragmentByTag("f${viewPager.currentItem}")

            if (currentFragment is SavingsTabFragment) {
                currentFragment.exportStatistics()
            } else if (currentFragment is UsageStatsFragment) {
                currentFragment.exportStatistics()
            }
        }
    }
}

// StatsPagerAdapter.kt
class StatsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> SavingsTabFragment()
            1 -> UsageStatsFragment()
            else -> throw IllegalArgumentException("Invalid position: $position")
        }
    }
}