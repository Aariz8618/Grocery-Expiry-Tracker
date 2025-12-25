package com.aariz.expirytracker

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class UsageStatsFragment : Fragment() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    private lateinit var tvItemsUsed: TextView
    private lateinit var tvItemsExpired: TextView
    private lateinit var tvLegendUsed: TextView
    private lateinit var tvLegendExpired: TextView
    private lateinit var tvLegendFresh: TextView
    private lateinit var tvMonthSummary: TextView
    private lateinit var tvEfficiencyRate: TextView
    private lateinit var tvTotalItems: TextView
    private lateinit var tvWasteSaved: TextView
    private lateinit var pieChart: PieChart
    private lateinit var barChart: BarChart

    private val dateFormatter by lazy {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).apply {
            isLenient = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_usage_stats, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        initViews(view)
        loadStatistics()
    }

    private fun initViews(view: View) {
        tvItemsUsed = view.findViewById(R.id.tv_items_used)
        tvItemsExpired = view.findViewById(R.id.tv_items_expired)
        tvLegendUsed = view.findViewById(R.id.tv_legend_used)
        tvLegendExpired = view.findViewById(R.id.tv_legend_expired)
        tvLegendFresh = view.findViewById(R.id.tv_legend_fresh)
        tvMonthSummary = view.findViewById(R.id.tv_month_summary)
        tvEfficiencyRate = view.findViewById(R.id.tv_efficiency_rate)
        tvTotalItems = view.findViewById(R.id.tv_total_items)
        tvWasteSaved = view.findViewById(R.id.tv_waste_saved)
        pieChart = view.findViewById(R.id.pie_chart)
        barChart = view.findViewById(R.id.bar_chart)
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        calculateAndDisplayStatistics(items)
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load statistics: ${result.exceptionOrNull()?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error loading statistics: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showEmptyState() {
        tvItemsUsed.text = "0"
        tvItemsExpired.text = "0"
        tvLegendUsed.text = "Used (0)"
        tvLegendExpired.text = "Expired (0)"
        tvLegendFresh.text = "Fresh (0)"
        tvMonthSummary.text = "Start tracking items to see your statistics!"
        tvEfficiencyRate.text = "0%"
        tvTotalItems.text = "0"
        tvWasteSaved.text = "0"

        setupPieChart(0, 0, 0)
        barChart.clear()
        barChart.invalidate()
    }

    private fun calculateAndDisplayStatistics(items: List<GroceryItem>) {
        val itemsWithUpdatedStatus = items.map { item ->
            val daysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(daysLeft, item.status)
            item.copy(daysLeft = daysLeft, status = actualStatus)
        }

        val usedCount = itemsWithUpdatedStatus.count { it.status == "used" }
        val expiredCount = itemsWithUpdatedStatus.count { it.status == "expired" }
        val freshCount = itemsWithUpdatedStatus.count {
            it.status == "fresh" || it.status == "expiring"
        }
        val totalItems = itemsWithUpdatedStatus.size

        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        val thisMonthItems = itemsWithUpdatedStatus.filter { item ->
            isFromCurrentMonth(item.createdAt, currentMonth, currentYear)
        }

        val monthUsedCount = thisMonthItems.count { it.status == "used" }
        val monthExpiredCount = thisMonthItems.count { it.status == "expired" }

        val nonFreshItems = usedCount + expiredCount
        val efficiencyRate = if (nonFreshItems > 0) {
            (usedCount.toFloat() / nonFreshItems.toFloat() * 100).toInt()
        } else {
            0
        }

        val itemsSaved = usedCount

        val monthlyTrends = calculateMonthlyTrends(itemsWithUpdatedStatus)

        updateUI(
            usedCount = usedCount,
            expiredCount = expiredCount,
            freshCount = freshCount,
            totalItems = totalItems,
            monthUsedCount = monthUsedCount,
            monthExpiredCount = monthExpiredCount,
            efficiencyRate = efficiencyRate,
            itemsSaved = itemsSaved
        )

        setupPieChart(usedCount, expiredCount, freshCount)
        setupBarChart(monthlyTrends)
    }

    private fun calculateMonthlyTrends(items: List<GroceryItem>): List<MonthData> {
        val calendar = Calendar.getInstance()
        val monthDataList = mutableListOf<MonthData>()

        for (i in 5 downTo 0) {
            calendar.time = Date()
            calendar.add(Calendar.MONTH, -i)

            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)
            val monthName = SimpleDateFormat("MMM", Locale.getDefault()).format(calendar.time)

            val monthItems = items.filter { item ->
                val itemCalendar = Calendar.getInstance()
                itemCalendar.time = item.createdAt
                itemCalendar.get(Calendar.MONTH) == month &&
                        itemCalendar.get(Calendar.YEAR) == year
            }

            val used = monthItems.count { it.status == "used" }
            val expired = monthItems.count { it.status == "expired" }

            monthDataList.add(MonthData(monthName, used, expired))
        }

        return monthDataList
    }

    private fun setupPieChart(usedCount: Int, expiredCount: Int, freshCount: Int) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        if (usedCount > 0) {
            entries.add(PieEntry(usedCount.toFloat(), "Used"))
            colors.add(Color.parseColor("#FF9800"))
        }
        if (expiredCount > 0) {
            entries.add(PieEntry(expiredCount.toFloat(), "Expired"))
            colors.add(Color.parseColor("#F44336"))
        }
        if (freshCount > 0) {
            entries.add(PieEntry(freshCount.toFloat(), "Fresh"))
            colors.add(Color.parseColor("#4CAF50"))
        }

        if (entries.isEmpty()) {
            entries.add(PieEntry(1f, "No Data"))
            colors.add(Color.parseColor("#E0E0E0"))
        }

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = colors
        dataSet.valueTextSize = 14f
        dataSet.valueTextColor = Color.WHITE
        dataSet.sliceSpace = 2f

        val data = PieData(dataSet)
        data.setValueFormatter(PercentFormatter(pieChart))

        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.legend.isEnabled = false
        pieChart.setDrawEntryLabels(false)
        pieChart.setUsePercentValues(true)
        pieChart.isRotationEnabled = false
        pieChart.setHoleColor(Color.TRANSPARENT)
        pieChart.holeRadius = 0f
        pieChart.transparentCircleRadius = 0f
        pieChart.animateY(1000)
        pieChart.invalidate()
    }

    private fun setupBarChart(monthlyData: List<MonthData>) {
        if (monthlyData.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        val usedEntries = ArrayList<BarEntry>()
        val expiredEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        monthlyData.forEachIndexed { index, data ->
            usedEntries.add(BarEntry(index.toFloat(), data.used.toFloat()))
            expiredEntries.add(BarEntry(index.toFloat(), data.expired.toFloat()))
            labels.add(data.month)
        }

        val usedDataSet = BarDataSet(usedEntries, "Used").apply {
            color = Color.parseColor("#FF9800")
            valueTextSize = 10f
            valueFormatter = IntValueFormatter()
        }

        val expiredDataSet = BarDataSet(expiredEntries, "Expired").apply {
            color = Color.parseColor("#F44336")
            valueTextSize = 10f
            valueFormatter = IntValueFormatter()
        }

        val barData = BarData(usedDataSet, expiredDataSet).apply {
            barWidth = 0.30f
        }

        barChart.data = barData
        barChart.description.isEnabled = false
        barChart.setFitBars(false)
        barChart.animateY(1000)

        val groupSpace = 0.30f
        val barSpace = 0.05f

        if (labels.isNotEmpty()) {
            barChart.xAxis.axisMinimum = 0f
            barChart.groupBars(0f, groupSpace, barSpace)
            val groupWidth = barChart.barData.getGroupWidth(groupSpace, barSpace)
            barChart.xAxis.axisMaximum = 0f + groupWidth * labels.size
        }

        val xAxis = barChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.granularity = 1f
        xAxis.setCenterAxisLabels(true)
        xAxis.setDrawGridLines(false)
        xAxis.textSize = 10f

        barChart.axisLeft.setDrawGridLines(true)
        barChart.axisLeft.granularity = 1f
        barChart.axisLeft.axisMinimum = 0f
        barChart.axisLeft.valueFormatter = IntValueFormatter()
        barChart.axisRight.isEnabled = false

        barChart.legend.isEnabled = true
        barChart.legend.textSize = 12f

        barChart.invalidate()
    }

    private class IntValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return if (value == 0f) "" else value.toInt().toString()
        }
    }

    private fun updateUI(
        usedCount: Int,
        expiredCount: Int,
        freshCount: Int,
        totalItems: Int,
        monthUsedCount: Int,
        monthExpiredCount: Int,
        efficiencyRate: Int,
        itemsSaved: Int
    ) {
        tvItemsUsed.text = usedCount.toString()
        tvItemsExpired.text = expiredCount.toString()
        tvLegendUsed.text = "Used ($usedCount)"
        tvLegendExpired.text = "Expired ($expiredCount)"
        tvLegendFresh.text = "Fresh ($freshCount)"
        tvMonthSummary.text = "This month: $monthUsedCount items used, $monthExpiredCount expired."

        val efficiencyText = when {
            efficiencyRate >= 80 -> "$efficiencyRate% 🌟"
            efficiencyRate >= 60 -> "$efficiencyRate% 👍"
            efficiencyRate >= 40 -> "$efficiencyRate% ⚠️"
            else -> "$efficiencyRate%"
        }
        tvEfficiencyRate.text = efficiencyText

        tvTotalItems.text = totalItems.toString()
        tvWasteSaved.text = itemsSaved.toString()
    }

    private fun calculateDaysLeft(expiryDate: String): Int {
        return try {
            val expiry = dateFormatter.parse(expiryDate) ?: return 0

            val expiryCalendar = Calendar.getInstance().apply {
                time = expiry
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val todayCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val diffInMillis = expiryCalendar.timeInMillis - todayCalendar.timeInMillis
            TimeUnit.MILLISECONDS.toDays(diffInMillis).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun determineStatus(daysLeft: Int, currentStatus: String): String {
        if (currentStatus == "used") return "used"

        return when {
            daysLeft < 0 -> "expired"
            daysLeft <= 3 -> "expiring"
            else -> "fresh"
        }
    }

    private fun isFromCurrentMonth(date: Date, currentMonth: Int, currentYear: Int): Boolean {
        val calendar = Calendar.getInstance()
        calendar.time = date

        val itemMonth = calendar.get(Calendar.MONTH)
        val itemYear = calendar.get(Calendar.YEAR)

        return itemMonth == currentMonth && itemYear == currentYear
    }

    fun exportStatistics() {
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    val itemsWithUpdatedStatus = items.map { item ->
                        val daysLeft = calculateDaysLeft(item.expiryDate)
                        val actualStatus = determineStatus(daysLeft, item.status)
                        item.copy(daysLeft = daysLeft, status = actualStatus)
                    }

                    val usedCount = itemsWithUpdatedStatus.count { it.status == "used" }
                    val expiredCount = itemsWithUpdatedStatus.count { it.status == "expired" }
                    val freshCount = itemsWithUpdatedStatus.count {
                        it.status == "fresh" || it.status == "expiring"
                    }

                    val savedValue = itemsWithUpdatedStatus
                        .filter { it.status == "used" }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    val wastedValue = itemsWithUpdatedStatus
                        .filter { it.status == "expired" }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    val shareText = buildString {
                        appendLine("📊 My Food Tracking Stats")
                        appendLine("Generated on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())}")
                        appendLine()
                        appendLine("🍽️ Total Items: ${itemsWithUpdatedStatus.size}")
                        appendLine("✅ Items Used: $usedCount")
                        appendLine("❌ Items Expired: $expiredCount")
                        appendLine("🌱 Fresh Items: $freshCount")
                        appendLine()

                        val nonFreshItems = usedCount + expiredCount
                        if (nonFreshItems > 0) {
                            val efficiency = (usedCount.toFloat() / nonFreshItems.toFloat() * 100).toInt()
                            appendLine("⚡ Efficiency Rate: $efficiency%")
                        }

                        if (savedValue > 0) {
                            appendLine("💰 Value Saved: ₹${String.format("%.2f", savedValue)}")
                        }

                        if (wastedValue > 0) {
                            appendLine("⚠️ Value Wasted: ₹${String.format("%.2f", wastedValue)}")
                        }

                        appendLine()
                        appendLine("Track your groceries with Expiry Tracker!")
                    }

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }

                    startActivity(Intent.createChooser(shareIntent, "Share Statistics"))

                } else {
                    Toast.makeText(requireContext(), "Failed to export statistics", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error exporting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class MonthData(
        val month: String,
        val used: Int,
        val expired: Int
    )
}