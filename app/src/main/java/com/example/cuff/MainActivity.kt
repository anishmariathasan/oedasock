package com.example.cuff

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.example.cuff.viewmodel.BleViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var pressureTextView: TextView
    private lateinit var inflateButton: Button
    private lateinit var deflateButton: Button
    private lateinit var emergencyStopButton: Button
    private lateinit var pressureBar: ProgressBar

    private val bleViewModel: BleViewModel by viewModels()
    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        statusTextView = findViewById(R.id.statusTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        inflateButton = findViewById(R.id.inflateButton)
        deflateButton = findViewById(R.id.deflateButton)
        emergencyStopButton = findViewById(R.id.emergencyStopButton)
        pressureBar = findViewById(R.id.pressureBar)

        // Set button click listeners
        inflateButton.setOnClickListener { bleViewModel.sendCommand("INFLATE") }
        deflateButton.setOnClickListener { bleViewModel.sendCommand("DEFLATE") }
        emergencyStopButton.setOnClickListener { bleViewModel.sendCommand("EMERGENCY_STOP") }

        // Observe connection state
        bleViewModel.connectionState.observe(this, Observer { state ->
            when (state) {
                BleViewModel.ConnectionState.CONNECTED -> {
                    statusTextView.text = "Connected to XIAO_ESP32C6"
                    enableButtons(true)
                }
                BleViewModel.ConnectionState.CONNECTING -> {
                    statusTextView.text = "Connecting..."
                    enableButtons(false)
                }
                BleViewModel.ConnectionState.DISCONNECTING -> {
                    statusTextView.text = "Disconnecting..."
                    enableButtons(false)
                }
                BleViewModel.ConnectionState.DISCONNECTED -> {
                    statusTextView.text = "Disconnected"
                    enableButtons(false)
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

        // Check for required permissions
        checkPermissionsAndStartScan()
    }

    private fun enableButtons(enabled: Boolean) {
        inflateButton.isEnabled = enabled
        deflateButton.isEnabled = enabled
        emergencyStopButton.isEnabled = enabled
    }

    private fun checkPermissionsAndStartScan() {
        val requiredPermissions = mutableListOf<String>()

        // Check for BLE scan permissions based on API level
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