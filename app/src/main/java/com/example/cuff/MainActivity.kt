package com.example.cuff

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.viewModels
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
    private lateinit var connectionStatusIcon: ImageView  // <-- New
    private lateinit var pressureTextView: TextView
    private lateinit var inflateButton: Button
    private lateinit var deflateButton: Button
    private lateinit var emergencyStopButton: Button
    private lateinit var pressureBar: ProgressBar
    private lateinit var loginOptionButton: Button
    private lateinit var savePressureButton: Button
    private lateinit var viewGraphsButton: Button
    private lateinit var logoutButton: Button
    private lateinit var prefs: SharedPreferences

    private val bleViewModel: BleViewModel by viewModels()
    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences and check login state
        prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
        val userEmail = prefs.getString("userEmail", null)

        // Initialize UI components
        connectionStatusIcon = findViewById(R.id.connectionStatusIcon)  // icon
        statusTextView = findViewById(R.id.statusTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        inflateButton = findViewById(R.id.inflateButton)
        deflateButton = findViewById(R.id.deflateButton)
        emergencyStopButton = findViewById(R.id.emergencyStopButton)
        pressureBar = findViewById(R.id.pressureBar)
        loginOptionButton = findViewById(R.id.loginOptionButton)
        savePressureButton = findViewById(R.id.savePressureButton)
        viewGraphsButton = findViewById(R.id.viewGraphsButton)
        logoutButton = findViewById(R.id.logoutButton)

        // Set visibility of login/logout and user control buttons based on login state
        if (userEmail == null) {
            loginOptionButton.visibility = View.VISIBLE
            savePressureButton.visibility = View.GONE
            viewGraphsButton.visibility = View.GONE
            logoutButton.visibility = View.GONE
        } else {
            loginOptionButton.visibility = View.GONE
            savePressureButton.visibility = View.VISIBLE
            viewGraphsButton.visibility = View.VISIBLE
            logoutButton.visibility = View.VISIBLE
        }

        // BLE command listeners
        inflateButton.setOnClickListener { bleViewModel.sendCommand("INFLATE") }
        deflateButton.setOnClickListener { bleViewModel.sendCommand("DEFLATE") }
        emergencyStopButton.setOnClickListener { bleViewModel.sendCommand("EMERGENCY_STOP") }

        // Login button navigates to LoginActivity
        loginOptionButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        // Logout button clears the login state
        logoutButton.setOnClickListener {
            prefs.edit().remove("userEmail").apply()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            finish()
            startActivity(intent)
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

        // Observe BLE connection state - the icons arent updaitng here i dont think
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
        // Observe scan state - this method actually updates it corretly
        bleViewModel.scanState.observe(this, Observer { scanState ->
            when (scanState) {
                BleViewModel.ScanState.SCANNING -> {
                    statusTextView.text = "Scanning..."
                    connectionStatusIcon.setImageResource(R.drawable.bluetooth_connecting)  // Use the scanning icon
                    enableButtons(false)  // Disable buttons during scan
                }
                BleViewModel.ScanState.IDLE -> {
                    statusTextView.text = "Scan idle"
                    connectionStatusIcon.setImageResource(R.drawable.ic_bluetooth_disconnected)  // Idle state image
                    enableButtons(true)  // Enable buttons when scan is idle
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
