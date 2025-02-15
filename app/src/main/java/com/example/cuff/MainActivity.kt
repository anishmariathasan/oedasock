package com.example.cuff
import com.example.cuff.R
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var pressureTextView: TextView
    private lateinit var inflateButton: Button
    private lateinit var deflateButton: Button
    private lateinit var emergencyStopButton: Button
    private lateinit var pressureBar: ProgressBar

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothSocket: BluetoothSocket? = null
    private val deviceAddress = "XX:XX:XX:XX:XX:XX" //need to get the actual
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Permission request codes
    private val BLUETOOTH_PERMISSIONS_REQUEST = 1

    // Activity result launcher for Bluetooth enable request
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkPermissionsAndConnect()
        } else {
            statusTextView.text = "Bluetooth Disabled"
            Toast.makeText(this, "Bluetooth must be enabled to use this app", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        statusTextView = findViewById(R.id.statusTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        inflateButton = findViewById(R.id.inflateButton)
        deflateButton = findViewById(R.id.deflateButton)
        emergencyStopButton = findViewById(R.id.emergencyStopButton)
        pressureBar = findViewById(R.id.pressureBar)

        // Check if device supports Bluetooth
        if (bluetoothAdapter == null) {
            statusTextView.text = "Bluetooth Not Supported"
            return
        }

        // Set up button listeners
        inflateButton.setOnClickListener {
            if (hasRequiredPermissions()) sendCommand("INFLATE")
            else checkPermissionsAndConnect()
        }
        deflateButton.setOnClickListener {
            if (hasRequiredPermissions()) sendCommand("DEFLATE")
            else checkPermissionsAndConnect()
        }
        emergencyStopButton.setOnClickListener {
            if (hasRequiredPermissions()) sendCommand("EMERGENCY_STOP")
            else checkPermissionsAndConnect()
        }

        // Initial connection attempt
        checkPermissionsAndConnect()
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkPermissionsAndConnect() {
        if (!bluetoothAdapter!!.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
            return
        }

        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            )
        }

        if (!hasRequiredPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                BLUETOOTH_PERMISSIONS_REQUEST
            )
        } else {
            connectBluetoothDevice()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            BLUETOOTH_PERMISSIONS_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    connectBluetoothDevice()
                } else {
                    statusTextView.text = "Permission Denied"
                    Toast.makeText(this, "Bluetooth permissions are required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun connectBluetoothDevice() {
        Thread {
            try {
                if (!hasRequiredPermissions()) {
                    runOnUiThread {
                        statusTextView.text = "Permission Required"
                        return@runOnUiThread
                    }
                    return@Thread
                }

                val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress)!!
                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket?.connect()
                runOnUiThread { statusTextView.text = "Connected" }
                listenForData()
            } catch (e: IOException) {
                e.printStackTrace()
                runOnUiThread { statusTextView.text = "Connection Failed" }
            } catch (e: SecurityException) {
                e.printStackTrace()
                runOnUiThread {
                    statusTextView.text = "Permission Required"
                    checkPermissionsAndConnect()
                }
            }
        }.start()
    }

    private fun sendCommand(command: String) {
        Thread {
            try {
                bluetoothSocket?.outputStream?.write(command.toByteArray())
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: SecurityException) {
                runOnUiThread {
                    statusTextView.text = "Permission Required"
                    checkPermissionsAndConnect()
                }
            }
        }.start()
    }

    private fun listenForData() {
        val buffer = ByteArray(1024)
        while (true) {
            try {
                val bytes = bluetoothSocket?.inputStream?.read(buffer) ?: -1
                if (bytes > 0) {
                    val data = String(buffer, 0, bytes)
                    val pressure = data.trim().toIntOrNull() ?: continue
                    runOnUiThread {
                        updatePressureUI(pressure)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                break
            } catch (e: SecurityException) {
                runOnUiThread {
                    statusTextView.text = "Permission Required"
                    checkPermissionsAndConnect()
                }
                break
            }
        }
    }

    private fun updatePressureUI(pressure: Int) {
        pressureTextView.text = "Pressure: $pressure/10"
        pressureBar.progress = pressure * 10
        when (pressure) {
            in 7..10 -> pressureBar.progressDrawable.setColorFilter(
                resources.getColor(android.R.color.holo_red_light), PorterDuff.Mode.SRC_IN)
            in 4..6 -> pressureBar.progressDrawable.setColorFilter(
                resources.getColor(android.R.color.holo_orange_light), PorterDuff.Mode.SRC_IN)
            else -> pressureBar.progressDrawable.setColorFilter(
                resources.getColor(android.R.color.holo_green_light), PorterDuff.Mode.SRC_IN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}