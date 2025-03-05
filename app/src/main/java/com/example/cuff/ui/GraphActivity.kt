package com.example.cuff.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.cuff.R
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.google.firebase.firestore.FirebaseFirestore

class GraphActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var backButton: Button
    private lateinit var pressureChart: LineChart
    private lateinit var weightChart: LineChart

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
        weightChart = findViewById(R.id.weightChart)

        // Handle back button click to return to MainActivity
        backButton.setOnClickListener {
            finish()  // Closes GraphActivity and returns to MainActivity
        }

        val db = FirebaseFirestore.getInstance()

        // Load pressure data from Firestore for the current user.
        db.collection("pressureData")
            .whereEqualTo("user", userEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val pressureEntries = ArrayList<Entry>()
                for (doc in querySnapshot.documents) {
                    val timestamp = doc.getLong("timestamp")?.toFloat() ?: 0f
                    val pressure = doc.getLong("pressure")?.toFloat() ?: 0f
                    pressureEntries.add(Entry(timestamp, pressure))
                }
                val pressureDataSet = LineDataSet(pressureEntries, "Pressure over Time")
                pressureDataSet.setDrawValues(false)
                pressureDataSet.setDrawCircles(true)
                val pressureLineData = LineData(pressureDataSet)
                pressureChart.data = pressureLineData
                pressureChart.invalidate() // refresh the chart
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading pressure data: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // Load weight data from Firestore for the current user.
        db.collection("weightData")
            .whereEqualTo("user", userEmail)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val weightEntries = ArrayList<Entry>()
                for (doc in querySnapshot.documents) {
                    val timestamp = doc.getLong("timestamp")?.toFloat() ?: 0f
                    val weight = doc.getDouble("weight")?.toFloat() ?: 0f
                    weightEntries.add(Entry(timestamp, weight))
                }
                val weightDataSet = LineDataSet(weightEntries, "Weight over Time")
                weightDataSet.setDrawValues(false)
                weightDataSet.setDrawCircles(true)
                val weightLineData = LineData(weightDataSet)
                weightChart.data = weightLineData
                weightChart.invalidate() // refresh the chart
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading weight data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
