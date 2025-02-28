package com.example.cuff

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var pressureTextView: TextView
    private lateinit var inflateButton: Button
    private lateinit var deflateButton: Button
    private lateinit var emergencyStopButton: Button
    private lateinit var pressureBar: ProgressBar

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var pressureCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false

    private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val TAG = "BLECuffApp"
    private val PERMISSION_REQUEST_CODE = 1
    private val SCAN_PERIOD: Long = 10000 // 10 seconds

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

        // Initialize BLE components
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            log("Bluetooth not supported on this device")
            return
        }

        // Set button click listeners
        inflateButton.setOnClickListener { sendCommand("INFLATE") }
        deflateButton.setOnClickListener { sendCommand("DEFLATE") }
        emergencyStopButton.setOnClickListener { sendCommand("EMERGENCY_STOP") }

        // Check for required permissions
        checkPermissionsAndStartScan()
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
            if (bluetoothAdapter?.isEnabled == true) {
                log("Starting BLE scan...")
                startScan()
            } else {
                log("Bluetooth is disabled")
            }
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
                if (bluetoothAdapter?.isEnabled == true) {
                    log("Starting BLE scan...")
                    startScan()
                } else {
                    log("Bluetooth is disabled")
                }
            } else {
                log("Required permissions not granted")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (isScanning) return
        isScanning = true

        log("Scanning for BLE devices...")

        // Do a broad scan without filtering by service UUID.
        val scanFilter = ScanFilter.Builder().build()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback)
            Handler(Looper.getMainLooper()).postDelayed({
                stopScan()
                if (bluetoothGatt == null) {
                    log("Device not found after scan timeout")
                }
            }, SCAN_PERIOD)
        } catch (e: Exception) {
            isScanning = false
            log("Scan error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (isScanning) {
            isScanning = false
            try {
                bleScanner?.stopScan(scanCallback)
                log("Scan stopped")
            } catch (e: Exception) {
                log("Error stopping scan: ${e.message}")
            }
        }
    }

    // The BLE scan callback
    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Try to get the advertised device name from either the device object or the scan record.
            var deviceName = result.device.name
            if (deviceName == null) {
                deviceName = result.scanRecord?.deviceName
            }
            if (deviceName != null && deviceName.contains("XIAO-BLE", ignoreCase = true)) {
                log("Found device: $deviceName, connecting...")
                stopScan()
                try {
                    bluetoothGatt = result.device.connectGatt(
                        this@MainActivity,
                        true,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } catch (e: Exception) {
                    log("Connection error: ${e.message}")
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            log("Scan failed with error code: $errorCode")
        }
    }

    // GATT callback - handles connection events and data exchange
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    log("Connected to GATT server")
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            log("Discovering services...")
                            gatt.discoverServices()
                        } catch (e: Exception) {
                            log("Error discovering services: ${e.message}")
                        }
                    }, 600)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    log("Disconnected from GATT server")
                    try {
                        bluetoothGatt?.close()
                        bluetoothGatt = null
                        pressureCharacteristic = null
                    } catch (e: Exception) {
                        log("Error closing GATT: ${e.message}")
                    }
                    Handler(Looper.getMainLooper()).postDelayed({
                        startScan()
                    }, 1000)
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    log("Service found")
                    pressureCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    if (pressureCharacteristic != null) {
                        try {
                            val success = gatt.setCharacteristicNotification(
                                pressureCharacteristic,
                                true
                            )
                            if (success) {
                                log("Characteristic notification enabled")
                                val descriptor = pressureCharacteristic?.getDescriptor(CCCD_UUID)
                                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                val descriptorWriteStarted = gatt.writeDescriptor(descriptor)
                                if (descriptorWriteStarted) {
                                    log("CCCD write initiated")
                                } else {
                                    log("Failed to initiate CCCD write")
                                }
                            } else {
                                log("Failed to enable notifications on characteristic")
                            }
                        } catch (e: Exception) {
                            log("Error enabling notifications: ${e.message}")
                        }
                    } else {
                        log("Characteristic not found")
                    }
                } else {
                    log("Service not found")
                }
            } else {
                log("Service discovery failed with status: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Notifications fully configured")
                runOnUiThread {
                    statusTextView.text = "Connected to XIAO_ESP32C6"
                }
            } else {
                log("Descriptor write failed, status: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == CHARACTERISTIC_UUID) {
                val data = characteristic.value
                val pressureString = String(data).trim()
                val pressure = pressureString.toIntOrNull()
                if (pressure != null) {
                    runOnUiThread { updatePressureUI(pressure) }
                } else {
                    log("Invalid pressure data: $pressureString")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val command = String(characteristic.value)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Command sent successfully: $command")
            } else {
                log("Failed to send command: $command, status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendCommand(command: String) {
        if (pressureCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(this, "BLE not connected", Toast.LENGTH_SHORT).show()
            log("Cannot send command - BLE not connected")
            return
        }
        try {
            pressureCharacteristic?.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            pressureCharacteristic?.value = command.toByteArray()
            val initiated = bluetoothGatt?.writeCharacteristic(pressureCharacteristic)
            if (initiated == true) {
                log("Sending command: $command")
            } else {
                log("Failed to initiate command: $command")
            }
        } catch (e: Exception) {
            log("Error sending command: ${e.message}")
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

    private fun log(message: String) {
        Log.d(TAG, message)
        runOnUiThread {
            statusTextView.text = message
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GATT connection: ${e.message}")
        }
    }
}
