package com.example.cuff.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cuff.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GraphActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var pressureChart: LineChart

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)

        prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userEmail = prefs.getString("userEmail", null)

        if (userEmail == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        backButton = findViewById(R.id.backButton)
        pressureChart = findViewById(R.id.pressureChart)

        // Handle back button click to return to MainActivity
        backButton.setOnClickListener {
            finish()  // Closes GraphActivity and returns to MainActivity
        }

        // Configure pressure chart
        setupChart(pressureChart, "Pressure Change Over Time")

        val db = FirebaseFirestore.getInstance()

        // Load pressure data from Firestore for the current user
        db.collection("pressureData")
            .whereEqualTo("user", userEmail)
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    Toast.makeText(this, "No pressure data available", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val pressureEntries = ArrayList<Entry>()
                val timestamps = ArrayList<Long>()

                // First, collect all documents and sort by timestamp
                for (doc in querySnapshot.documents) {
                    val timestamp = doc.getLong("timestamp") ?: 0L
                    val pressure = doc.getLong("pressure") ?: 0L

                    // Use index as x-value for better visualization
                    timestamps.add(timestamp)
                    pressureEntries.add(Entry(timestamps.size.toFloat() - 1, pressure.toFloat()))
                }

                val pressureDataSet = LineDataSet(pressureEntries, "Pressure (0-10)")
                styleDataSet(pressureDataSet, R.color.colorPrimary)

                val pressureLineData = LineData(pressureDataSet)
                pressureChart.data = pressureLineData

                // Configure X axis to show dates
                pressureChart.xAxis.valueFormatter = DateAxisValueFormatter(timestamps)

                // Set Y axis range to 0-10
                pressureChart.axisLeft.axisMinimum = 0f
                pressureChart.axisLeft.axisMaximum = 10f
                pressureChart.axisRight.axisMinimum = 0f
                pressureChart.axisRight.axisMaximum = 10f

                pressureChart.invalidate() // refresh the chart
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading pressure data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupChart(chart: LineChart, title: String) {
        chart.description.text = title
        chart.setTouchEnabled(true)
        chart.isDragEnabled = true
        chart.setScaleEnabled(true)

        // X-Axis setup
        val xAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f

        // Hide grid lines
        chart.axisLeft.setDrawGridLines(false)
        chart.axisRight.setDrawGridLines(false)
        xAxis.setDrawGridLines(false)

        // Customize legend
        chart.legend.isEnabled = true

        // Hide right axis
        chart.axisRight.isEnabled = false
    }

    private fun styleDataSet(dataSet: LineDataSet, colorResId: Int) {
        dataSet.color = resources.getColor(colorResId, null)
        dataSet.setCircleColor(resources.getColor(colorResId, null))
        dataSet.lineWidth = 2f
        dataSet.circleRadius = 4f
        dataSet.setDrawCircleHole(false)
        dataSet.valueTextSize = 9f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = resources.getColor(colorResId, null)
        dataSet.fillAlpha = 30
        dataSet.setDrawValues(false)
    }

    // Custom formatter for the X-axis to display dates
    inner class DateAxisValueFormatter(private val timestamps: ArrayList<Long>) : ValueFormatter() {
        private val dateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        override fun getFormattedValue(value: Float): String {
            // Convert index to timestamp
            val index = value.toInt()
            return if (index >= 0 && index < timestamps.size) {
                dateFormat.format(Date(timestamps[index]))
            } else {
                ""
            }
        }
    }
}
