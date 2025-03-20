package com.example.cuff

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.example.cuff.ui.LoginActivity
import com.example.cuff.viewmodel.BleViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.example.cuff.ui.GraphActivity

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var connectionStatusIcon: ImageView
    private lateinit var pressureTextView: TextView
    private lateinit var inflateButton: Button
    private lateinit var deflateButton: Button
    private lateinit var emergencyStopButton: Button
    private lateinit var pressureBar: ProgressBar
    private lateinit var loginOptionButton: Button
    private lateinit var recalibrateButton: Button
    private lateinit var savePressureButton: Button
    private lateinit var viewGraphsButton: Button
    private lateinit var logoutButton: Button
    private lateinit var waterConsumptionButton: Button
    private lateinit var dataRecordingTitle: TextView
    private lateinit var prefs: SharedPreferences

    private val bleViewModel: BleViewModel by viewModels()
    private val PERMISSION_REQUEST_CODE = 1
    private val LOGIN_REQUEST_CODE = 100  // Added request code for login activity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences and check login state
        prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userEmail = prefs.getString("userEmail", null)

        // Initialize UI components
        connectionStatusIcon = findViewById(R.id.connectionStatusIcon)
        statusTextView = findViewById(R.id.statusTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        inflateButton = findViewById(R.id.inflateButton)
        deflateButton = findViewById(R.id.deflateButton)
        emergencyStopButton = findViewById(R.id.emergencyStopButton)
        pressureBar = findViewById(R.id.pressureBar)
        loginOptionButton = findViewById(R.id.loginOptionButton)
        recalibrateButton = findViewById(R.id.recalibrateButton)
        savePressureButton = findViewById(R.id.savePressureButton)
        viewGraphsButton = findViewById(R.id.viewGraphsButton)
        logoutButton = findViewById(R.id.logoutButton)
        waterConsumptionButton = findViewById(R.id.waterConsumptionButton)

        // Set visibility of login/logout and user control buttons based on login state
        updateUIBasedOnLoginState(userEmail)

        // BLE command listeners
        inflateButton.setOnClickListener { bleViewModel.sendCommand("INFLATE") }
        deflateButton.setOnClickListener { bleViewModel.sendCommand("DEFLATE") }
        emergencyStopButton.setOnClickListener { bleViewModel.sendCommand("EMERGENCY_STOP") }

        // Login button navigates to LoginActivity using startActivityForResult
        loginOptionButton.setOnClickListener {
            startActivityForResult(Intent(this, LoginActivity::class.java), LOGIN_REQUEST_CODE)
        }

        // Recalibrate button handler
        recalibrateButton.setOnClickListener {
            performCalibration()
        }

        // Logout button clears the login state and updates UI without restarting activity
        logoutButton.setOnClickListener {
            prefs.edit().remove("userEmail").apply()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            updateUIBasedOnLoginState(null)
        }

        // Water consumption button shows dialog to input water consumption
        waterConsumptionButton.setOnClickListener {
            showWaterConsumptionDialog()
        }

        // Save Pressure button saves current pressure to Firestore with timestamp
        savePressureButton.setOnClickListener {
            val currentPressure = bleViewModel.pressureData.value ?: 0
            val timestamp = System.currentTimeMillis()
            val db = FirebaseFirestore.getInstance()
            val data = hashMapOf(
                "user" to userEmail,
                "pressure" to currentPressure,
                "timestamp" to timestamp
            )
            db.collection("pressureData")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Pressure data saved", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }

        // View Graphs button launches GraphActivity
        viewGraphsButton.setOnClickListener {
            startActivity(Intent(this, GraphActivity::class.java))
        }

        // Observe BLE connection state
        bleViewModel.connectionState.observe(this, Observer { state ->
            when (state) {
                BleViewModel.ConnectionState.CONNECTED -> {
                    statusTextView.text = "Connected to XIAO_ESP32C6"
                    connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_connected)
                    enableButtons(true)
                }
                BleViewModel.ConnectionState.CONNECTING -> {
                    statusTextView.text = "Connecting..."
                    connectionStatusIcon.setImageResource(R.drawable.bluetooth_connecting)
                    enableButtons(false)
                }
                BleViewModel.ConnectionState.DISCONNECTING -> {
                    statusTextView.text = "Disconnecting..."
                    connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)
                    enableButtons(false)
                }
                BleViewModel.ConnectionState.DISCONNECTED -> {
                    statusTextView.text = "Disconnected"
                    connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)
                    enableButtons(false)
                }
            }
        })

        // Observe scan state
        bleViewModel.scanState.observe(this, Observer { scanState ->
            when (scanState) {
                BleViewModel.ScanState.SCANNING -> {
                    statusTextView.text = "Scanning..."
                    connectionStatusIcon.setImageResource(R.drawable.bluetooth_connecting)
                    enableButtons(false)
                }
                BleViewModel.ScanState.IDLE -> {
                    statusTextView.text = "Scan idle"
                    connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)
                    enableButtons(true)
                }
            }
        })

        // Observe pressure data
        bleViewModel.pressureData.observe(this, Observer { pressure ->
            updatePressureUI(pressure)
        })

        // Observe log messages
        bleViewModel.logMessage.observe(this, Observer { message ->
            statusTextView.text = message
        })

        // Check for required permissions and start BLE scan
        checkPermissionsAndStartScan()
    }

    // Method to perform calibration
    private fun performCalibration() {
        // Only send CALIBRATE if connected
        if (bleViewModel.connectionState.value == BleViewModel.ConnectionState.CONNECTED) {
            bleViewModel.sendCommand("CALIBRATE")

            // Show calibrating status
            statusTextView.text = "Calibrating..."

            // Simulate completion after 2 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                statusTextView.text = "Calibration done!"
                // Reset to normal status after another 2 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    statusTextView.text = "Connected to XIAO_ESP32C6"
                }, 2000)
            }, 2000)
        } else {
            statusTextView.text = "Not connected. Cannot calibrate."
            // Reset after 2 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                statusTextView.text = when (bleViewModel.connectionState.value) {
                    BleViewModel.ConnectionState.CONNECTED -> "Connected to XIAO_ESP32C6"
                    BleViewModel.ConnectionState.CONNECTING -> "Connecting..."
                    BleViewModel.ConnectionState.DISCONNECTING -> "Disconnecting..."
                    else -> "Disconnected"
                }
            }, 2000)
        }
    }

    // Method to show water consumption input dialog
    private fun showWaterConsumptionDialog() {
        val userEmail = prefs.getString("userEmail", null) ?: return

        val input = EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        input.hint = "Enter glasses of water (0-10)"

        AlertDialog.Builder(this)
            .setTitle("Water Consumption")
            .setMessage("How many glasses of water did you drink today?")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val waterAmount = input.text.toString().toIntOrNull() ?: 0
                if (waterAmount in 1..20) {
                    saveWaterConsumption(userEmail, waterAmount)
                } else {
                    Toast.makeText(this, "Please enter a value between 1 and 20", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Method to save water consumption to Firestore
    private fun saveWaterConsumption(userEmail: String, glasses: Int) {
        val timestamp = System.currentTimeMillis()
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "user" to userEmail,
            "waterGlasses" to glasses,
            "timestamp" to timestamp,
            "date" to java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
        )

        db.collection("waterConsumption")
            .add(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Water consumption saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // New method to update UI based on login state
    private fun updateUIBasedOnLoginState(userEmail: String?) {
        if (userEmail == null) {
            loginOptionButton.visibility = View.VISIBLE
            savePressureButton.visibility = View.GONE
            viewGraphsButton.visibility = View.GONE
            logoutButton.visibility = View.GONE
            waterConsumptionButton.visibility = View.GONE
            dataRecordingTitle.visibility = View.GONE
        } else {
            loginOptionButton.visibility = View.GONE
            savePressureButton.visibility = View.VISIBLE
            viewGraphsButton.visibility = View.VISIBLE
            logoutButton.visibility = View.VISIBLE
            waterConsumptionButton.visibility = View.VISIBLE
            dataRecordingTitle.visibility = View.VISIBLE
        }
    }

    // Handle results from activities started with startActivityForResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
            // Update UI based on new login state
            val userEmail = prefs.getString("userEmail", null)
            updateUIBasedOnLoginState(userEmail)
        }
    }

    private fun enableButtons(enabled: Boolean) {
        inflateButton.isEnabled = enabled
        deflateButton.isEnabled = enabled
        emergencyStopButton.isEnabled = enabled
    }

    private fun checkPermissionsAndStartScan() {
        val requiredPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (requiredPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            bleViewModel.startScan()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                bleViewModel.startScan()
            } else {
                Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePressureUI(pressure: Int) {
        pressureTextView.text = "Pressure: $pressure/10"
        pressureBar.progress = pressure * 10
        val colorRes = when (pressure) {
            in 7..10 -> android.R.color.holo_red_light
            in 4..6 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_green_light
        }
        pressureBar.progressDrawable.setColorFilter(
            ContextCompat.getColor(this, colorRes),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        bleViewModel.stopScan()
        bleViewModel.disconnect()
    }
}