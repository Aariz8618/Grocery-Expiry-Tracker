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
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class SavingsTabFragment : Fragment() {

    private lateinit var firestoreRepository: FirestoreRepository
    private lateinit var auth: FirebaseAuth

    private lateinit var tvMoneySaved: TextView
    private lateinit var tvMoneyWasted: TextView
    private lateinit var tvLegendSaved: TextView
    private lateinit var tvLegendWasted: TextView
    private lateinit var tvSavingsSummary: TextView
    private lateinit var tvItemsUsedCount: TextView
    private lateinit var tvValueSaved: TextView
    private lateinit var tvItemsExpiredCount: TextView
    private lateinit var tvValueWasted: TextView
    private lateinit var tvAvgItemValue: TextView
    private lateinit var pieChartSavings: PieChart
    private lateinit var barChartSavings: BarChart

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
        return inflater.inflate(R.layout.fragment_savings_tab, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestoreRepository = FirestoreRepository()

        initViews(view)
        loadSavingsData()
    }

    private fun initViews(view: View) {
        tvMoneySaved = view.findViewById(R.id.tv_money_saved)
        tvMoneyWasted = view.findViewById(R.id.tv_money_wasted)
        tvLegendSaved = view.findViewById(R.id.tv_legend_saved)
        tvLegendWasted = view.findViewById(R.id.tv_legend_wasted)
        tvSavingsSummary = view.findViewById(R.id.tv_savings_summary)
        tvItemsUsedCount = view.findViewById(R.id.tv_items_used_count)
        tvValueSaved = view.findViewById(R.id.tv_value_saved)
        tvItemsExpiredCount = view.findViewById(R.id.tv_items_expired_count)
        tvValueWasted = view.findViewById(R.id.tv_value_wasted)
        tvAvgItemValue = view.findViewById(R.id.tv_avg_item_value)
        pieChartSavings = view.findViewById(R.id.pie_chart_savings)
        barChartSavings = view.findViewById(R.id.bar_chart_savings)
    }

    private fun loadSavingsData() {
        lifecycleScope.launch {
            try {
                val result = firestoreRepository.getUserGroceryItems()

                if (result.isSuccess) {
                    val items = result.getOrNull() ?: emptyList()

                    if (items.isEmpty()) {
                        showEmptyState()
                    } else {
                        calculateAndDisplaySavings(items)
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Failed to load savings data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Error loading data: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showEmptyState() {
        tvMoneySaved.text = "₹0"
        tvMoneyWasted.text = "₹0"
        tvLegendSaved.text = "Saved (₹0)"
        tvLegendWasted.text = "Wasted (₹0)"
        tvSavingsSummary.text = "Start tracking items to see your savings!"
        tvItemsUsedCount.text = "0"
        tvValueSaved.text = "₹0"
        tvItemsExpiredCount.text = "0"
        tvValueWasted.text = "₹0"
        tvAvgItemValue.text = "₹0"

        setupPieChart(0f, 0f)
        barChartSavings.clear()
        barChartSavings.invalidate()
    }

    private fun calculateAndDisplaySavings(items: List<GroceryItem>) {
        // Update status for all items
        val itemsWithUpdatedStatus = items.map { item ->
            val daysLeft = calculateDaysLeft(item.expiryDate)
            val actualStatus = determineStatus(daysLeft, item.status)
            item.copy(daysLeft = daysLeft, status = actualStatus)
        }

        // Calculate counts
        val usedCount = itemsWithUpdatedStatus.count { it.status == "used" }
        val expiredCount = itemsWithUpdatedStatus.count { it.status == "expired" }

        // Calculate monetary values using 'amount' field
        val savedValue = itemsWithUpdatedStatus
            .filter { it.status == "used" }
            .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

        val wastedValue = itemsWithUpdatedStatus
            .filter { it.status == "expired" }
            .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

        // Calculate average item value
        val totalValue = itemsWithUpdatedStatus.sumOf {
            it.amount.toDoubleOrNull() ?: 0.0
        }
        val avgValue = if (itemsWithUpdatedStatus.isNotEmpty()) {
            totalValue / itemsWithUpdatedStatus.size
        } else 0.0

        // Update UI
        updateSavingsUI(
            usedCount = usedCount,
            expiredCount = expiredCount,
            savedValue = savedValue,
            wastedValue = wastedValue,
            avgValue = avgValue
        )

        // Setup charts
        setupPieChart(savedValue.toFloat(), wastedValue.toFloat())
        setupBarChart(itemsWithUpdatedStatus)
    }

    private fun updateSavingsUI(
        usedCount: Int,
        expiredCount: Int,
        savedValue: Double,
        wastedValue: Double,
        avgValue: Double
    ) {
        // Format values
        val savedFormatted = String.format("₹%.0f", savedValue)
        val wastedFormatted = String.format("₹%.0f", wastedValue)
        val avgFormatted = String.format("₹%.0f", avgValue)
        val netSavings = savedValue - wastedValue

        // Update cards
        tvMoneySaved.text = savedFormatted
        tvMoneyWasted.text = wastedFormatted

        // Update legends
        tvLegendSaved.text = "Saved ($savedFormatted)"
        tvLegendWasted.text = "Wasted ($wastedFormatted)"

        // Update summary
        tvSavingsSummary.text = "Total savings this month: ${String.format("₹%.0f", netSavings)}. " +
                "You saved $savedFormatted by using $usedCount items before expiry."

        // Update breakdown
        tvItemsUsedCount.text = usedCount.toString()
        tvValueSaved.text = savedFormatted
        tvItemsExpiredCount.text = expiredCount.toString()
        tvValueWasted.text = wastedFormatted
        tvAvgItemValue.text = avgFormatted
    }

    private fun setupPieChart(savedValue: Float, wastedValue: Float) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        if (savedValue > 0) {
            entries.add(PieEntry(savedValue, "Saved"))
            colors.add(Color.parseColor("#4CAF50"))
        }
        if (wastedValue > 0) {
            entries.add(PieEntry(wastedValue, "Wasted"))
            colors.add(Color.parseColor("#F44336"))
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

        // Format values to show currency
        dataSet.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return "₹${value.toInt()}"
            }
        }

        val data = PieData(dataSet)
        pieChartSavings.data = data

        // Customize pie chart
        pieChartSavings.setUsePercentValues(false)
        pieChartSavings.description.isEnabled = false
        pieChartSavings.isDrawHoleEnabled = true
        pieChartSavings.setHoleColor(Color.WHITE)
        pieChartSavings.holeRadius = 40f
        pieChartSavings.transparentCircleRadius = 45f
        pieChartSavings.setDrawEntryLabels(false)
        pieChartSavings.legend.isEnabled = false
        pieChartSavings.setTouchEnabled(true)

        pieChartSavings.animateY(1000)
        pieChartSavings.invalidate()
    }

    private fun setupBarChart(items: List<GroceryItem>) {
        val calendar = Calendar.getInstance()
        val monthDataList = mutableListOf<MonthSavingsData>()

        // Get last 6 months
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

            val savedValue = monthItems
                .filter { it.status == "used" }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

            val wastedValue = monthItems
                .filter { it.status == "expired" }
                .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

            monthDataList.add(MonthSavingsData(monthName, savedValue, wastedValue))
        }

        if (monthDataList.isEmpty()) {
            barChartSavings.clear()
            barChartSavings.invalidate()
            return
        }

        val savedEntries = ArrayList<BarEntry>()
        val wastedEntries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        monthDataList.forEachIndexed { index, data ->
            savedEntries.add(BarEntry(index.toFloat(), data.savedValue.toFloat()))
            wastedEntries.add(BarEntry(index.toFloat(), data.wastedValue.toFloat()))
            labels.add(data.month)
        }

        val savedDataSet = BarDataSet(savedEntries, "Saved")
        savedDataSet.color = Color.parseColor("#4CAF50")
        savedDataSet.valueTextSize = 10f

        val wastedDataSet = BarDataSet(wastedEntries, "Wasted")
        wastedDataSet.color = Color.parseColor("#F44336")
        wastedDataSet.valueTextSize = 10f

        // Format values
        val valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return if (value == 0f) "" else "₹${value.toInt()}"
            }
        }
        savedDataSet.valueFormatter = valueFormatter
        wastedDataSet.valueFormatter = valueFormatter

        val data = BarData(savedDataSet, wastedDataSet)
        data.barWidth = 0.35f

        barChartSavings.data = data
        barChartSavings.description.isEnabled = false
        barChartSavings.setFitBars(true)
        barChartSavings.animateY(1000)

        // Group bars
        val groupSpace = 0.3f
        val barSpace = 0.05f
        barChartSavings.groupBars(0f, groupSpace, barSpace)
        barChartSavings.xAxis.axisMinimum = 0f
        barChartSavings.xAxis.axisMaximum = labels.size.toFloat()

        // X-axis configuration
        val xAxis = barChartSavings.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.labelCount = labels.size

        // Y-axis configuration
        barChartSavings.axisLeft.setDrawGridLines(true)
        barChartSavings.axisRight.isEnabled = false

        barChartSavings.invalidate()
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

                    val savedValue = itemsWithUpdatedStatus
                        .filter { it.status == "used" }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    val wastedValue = itemsWithUpdatedStatus
                        .filter { it.status == "expired" }
                        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

                    val shareText = buildString {
                        appendLine("💰 My Money Savings Stats")
                        appendLine("Generated on ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())}")
                        appendLine()
                        appendLine("✅ Items Used: $usedCount")
                        appendLine("💚 Money Saved: ₹${String.format("%.2f", savedValue)}")
                        appendLine()
                        appendLine("❌ Items Expired: $expiredCount")
                        appendLine("💸 Money Wasted: ₹${String.format("%.2f", wastedValue)}")
                        appendLine()
                        val netSavings = savedValue - wastedValue
                        appendLine("📊 Net Savings: ₹${String.format("%.2f", netSavings)}")
                        appendLine()
                        appendLine("Track your groceries with Expiry Tracker!")
                    }

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }

                    startActivity(Intent.createChooser(shareIntent, "Share Savings"))

                } else {
                    Toast.makeText(requireContext(), "Failed to export statistics", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error exporting: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class MonthSavingsData(
        val month: String,
        val savedValue: Double,
        val wastedValue: Double
    )
}