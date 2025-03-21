package com.example.cuff

import android.graphics.Color
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
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ClipDrawable
import android.view.Gravity

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
        dataRecordingTitle = findViewById(R.id.dataRecordingTitle)

        // Set up progress bar with initial gray background
        setupProgressBar()

        // Set visibility of login/logout and user control buttons based on login state
        updateUIBasedOnLoginState(userEmail)

        // BLE command listeners
        inflateButton.setOnClickListener {
            try {
                bleViewModel.sendCommand("INFLATE")
            } catch (e: Exception) {
                Toast.makeText(this, "Error inflating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        deflateButton.setOnClickListener {
            try {
                bleViewModel.sendCommand("DEFLATE")
            } catch (e: Exception) {
                Toast.makeText(this, "Error deflating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        emergencyStopButton.setOnClickListener {
            try {
                bleViewModel.sendCommand("EMERGENCY_STOP")
            } catch (e: Exception) {
                Toast.makeText(this, "Error stopping: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Login button navigates to LoginActivity using startActivityForResult
        loginOptionButton.setOnClickListener {
            try {
                startActivityForResult(Intent(this, LoginActivity::class.java), LOGIN_REQUEST_CODE)
            } catch (e: Exception) {
                Toast.makeText(this, "Error launching login: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Recalibrate button handler
        recalibrateButton.setOnClickListener {
            try {
                performCalibration()
            } catch (e: Exception) {
                Toast.makeText(this, "Error calibrating: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Logout button clears the login state and updates UI without restarting activity
        logoutButton.setOnClickListener {
            try {
                prefs.edit().remove("userEmail").apply()
                Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                updateUIBasedOnLoginState(null)
            } catch (e: Exception) {
                Toast.makeText(this, "Error logging out: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Water consumption button shows dialog to input water consumption
        waterConsumptionButton.setOnClickListener {
            try {
                showWaterConsumptionDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Error showing dialog: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Save Pressure button saves current pressure to Firestore with timestamp
        savePressureButton.setOnClickListener {
            try {
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
            } catch (e: Exception) {
                Toast.makeText(this, "Error saving pressure: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // View Graphs button launches GraphActivity
        viewGraphsButton.setOnClickListener {
            try {
                startActivity(Intent(this, GraphActivity::class.java))
            } catch (e: Exception) {
                Toast.makeText(this, "Error showing graphs: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Observe BLE connection state - give priority to connection state over scan state
        bleViewModel.connectionState.observe(this, Observer { state ->
            try {
                when (state) {
                    BleViewModel.ConnectionState.CONNECTED -> {
                        statusTextView.text = "Connected"
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
                        // Only update text if not scanning
                        if (bleViewModel.scanState.value != BleViewModel.ScanState.SCANNING) {
                            statusTextView.text = "Disconnected"
                            connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)
                        }
                        enableButtons(false)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "UI update error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Observe scan state - only update UI based on scan state if not connected
        bleViewModel.scanState.observe(this, Observer { scanState ->
            try {
                // Only update UI if not connected
                if (bleViewModel.connectionState.value != BleViewModel.ConnectionState.CONNECTED) {
                    when (scanState) {
                        BleViewModel.ScanState.SCANNING -> {
                            statusTextView.text = "Scanning..."
                            connectionStatusIcon.setImageResource(R.drawable.bluetooth_connecting)
                            enableButtons(false)
                        }
                        BleViewModel.ScanState.IDLE -> {
                            // Only show idle if disconnected
                            if (bleViewModel.connectionState.value == BleViewModel.ConnectionState.DISCONNECTED) {
                                statusTextView.text = "Ready to connect"
                                connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Scan state update error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Observe pressure data
        bleViewModel.pressureData.observe(this, Observer { pressure ->
            try {
                updatePressureUI(pressure)
            } catch (e: Exception) {
                Toast.makeText(this, "Pressure update error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Observe log messages - be selective about which log messages we show on UI
        bleViewModel.logMessage.observe(this, Observer { message ->
            try {
                // Only update status text with log messages if they're important
                // and not redundant with connection/scan state
                if (!message.contains("Scan ") &&  // Don't show scan messages
                    !message.contains("Connecting") &&
                    !message.contains("Connected") &&
                    !message.contains("Disconnected")) {
                    statusTextView.text = message
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Log message update error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Check for required permissions and start BLE scan
        try {
            checkPermissionsAndStartScan()
        } catch (e: Exception) {
            Toast.makeText(this, "Error starting scan: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // New method to set up the progress bar with gray background
    private fun setupProgressBar() {
        try {
            // Ensure the progress bar's maximum is set to 100
            pressureBar.max = 100

            // Set the initial color of the progress bar
            val drawable = pressureBar.progressDrawable
            if (drawable is LayerDrawable) {
                // Get the background (track) drawable - usually at index 0
                val backgroundDrawable = drawable.findDrawableByLayerId(android.R.id.background)
                // Set the background to gray
                backgroundDrawable?.setColorFilter(Color.LTGRAY, android.graphics.PorterDuff.Mode.SRC_IN)

                // Get the progress drawable (foreground) - usually at index 1 or android.R.id.progress
                val progressDrawable = drawable.findDrawableByLayerId(android.R.id.progress)
                // Set initial color as green
                progressDrawable?.setColorFilter(Color.GREEN, android.graphics.PorterDuff.Mode.SRC_IN)
            }

            // Initial progress is 0
            pressureBar.progress = 0
        } catch (e: Exception) {
            Toast.makeText(this, "Progress bar setup error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
                    statusTextView.text = "Connected"
                }, 2000)
            }, 2000)
        } else {
            statusTextView.text = "Not connected. Cannot calibrate."
            // Reset after 2 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                statusTextView.text = when (bleViewModel.connectionState.value) {
                    BleViewModel.ConnectionState.CONNECTED -> "Connected"
                    BleViewModel.ConnectionState.CONNECTING -> "Connecting..."
                    BleViewModel.ConnectionState.DISCONNECTING -> "Disconnecting..."
                    else -> if (bleViewModel.scanState.value == BleViewModel.ScanState.SCANNING) "Scanning..." else "Disconnected"
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

    // Updated method to check if views are initialized
    private fun updateUIBasedOnLoginState(userEmail: String?) {
        // Safely check all required views are initialized
        if (!::loginOptionButton.isInitialized ||
            !::savePressureButton.isInitialized ||
            !::viewGraphsButton.isInitialized ||
            !::logoutButton.isInitialized ||
            !::waterConsumptionButton.isInitialized ||
            !::dataRecordingTitle.isInitialized) {
            return
        }

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
        try {
            if (requestCode == LOGIN_REQUEST_CODE && resultCode == RESULT_OK) {
                // Update UI based on new login state
                val userEmail = prefs.getString("userEmail", null)
                updateUIBasedOnLoginState(userEmail)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Activity result error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enableButtons(enabled: Boolean) {
        try {
            inflateButton.isEnabled = enabled
            deflateButton.isEnabled = enabled
            emergencyStopButton.isEnabled = enabled
        } catch (e: Exception) {
            Toast.makeText(this, "Button enable error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
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
        try {
            if (requestCode == PERMISSION_REQUEST_CODE) {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    bleViewModel.startScan()
                } else {
                    Toast.makeText(this, "Required permissions not granted", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Permission result error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Updated method to fix color transition issues
    private fun updatePressureUI(pressure: Int) {
        try {
            // Display the pressure value out of 100
            pressureTextView.text = "Pressure: $pressure/100"

            // Set progress directly as the value is already in the 0-100 range
            pressureBar.progress = pressure

            // Calculate continuous color transition: green (0,255,0) at 0 pressure to red (255,0,0) at 100 pressure
            val red = (pressure * 255 / 100).coerceIn(0, 255)
            val green = ((100 - pressure) * 255 / 100).coerceIn(0, 255)
            val blue = 0
            val interpolatedColor = Color.rgb(red, green, blue)

            // Get the progress drawable
            val drawable = pressureBar.progressDrawable
            if (drawable is LayerDrawable) {
                try {
                    // Make sure we apply the color only to the progress part
                    val progressDrawable = drawable.findDrawableByLayerId(android.R.id.progress)
                    progressDrawable?.setColorFilter(interpolatedColor, android.graphics.PorterDuff.Mode.SRC_IN)
                } catch (e: Exception) {
                    // If we can't apply the color to just the progress part, apply to the whole bar
                    pressureBar.progressDrawable.setColorFilter(interpolatedColor, android.graphics.PorterDuff.Mode.SRC_IN)
                }
            } else {
                // If we can't cast to LayerDrawable, just apply the filter to the drawable directly
                pressureBar.progressDrawable.setColorFilter(interpolatedColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        } catch (e: Exception) {
            // Log the error without showing a toast to avoid infinite loop of errors
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()
            bleViewModel.stopScan()
            bleViewModel.disconnect()
        } catch (e: Exception) {
            // Can't show Toast here as activity is being destroyed
            e.printStackTrace()
        }
    }
}