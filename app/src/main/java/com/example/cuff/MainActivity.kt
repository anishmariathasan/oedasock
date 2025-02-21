package com.example.cuff

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    private val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
    private val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTextView = findViewById(R.id.statusTextView)
        pressureTextView = findViewById(R.id.pressureTextView)
        inflateButton = findViewById(R.id.inflateButton)
        deflateButton = findViewById(R.id.deflateButton)
        emergencyStopButton = findViewById(R.id.emergencyStopButton)
        pressureBar = findViewById(R.id.pressureBar)

        bluetoothAdapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothAdapter == null) {
            statusTextView.text = "Bluetooth not supported"
            return
        }

        inflateButton.setOnClickListener { sendCommand("INFLATE") }
        deflateButton.setOnClickListener { sendCommand("DEFLATE") }
        emergencyStopButton.setOnClickListener { sendCommand("EMERGENCY_STOP") }

        checkPermissionsAndScan()
    }

    private fun checkPermissionsAndScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ), 1
            )
        } else {
            scanForDevice()
        }
    }

    private fun scanForDevice() {
        statusTextView.text = "Scanning..."
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (result.device.name == "XIAO_ESP32C6") {
                    statusTextView.text = "Device found, connecting..."
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            bluetoothGatt = result.device.connectGatt(this@MainActivity, false, gattCallback)
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                            runOnUiThread { statusTextView.text = "Permission Error: BLUETOOTH_CONNECT" }
                        }
                    } else {
                        runOnUiThread { statusTextView.text = "BLUETOOTH_CONNECT permission not granted" }
                    }
                    bleScanner?.stopScan(this)
                }
            }
        }
        bleScanner?.startScan(scanCallback)
        Handler(Looper.getMainLooper()).postDelayed({ bleScanner?.stopScan(scanCallback) }, 10000)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread { statusTextView.text = "Connected, discovering services..." }
                // Fix #1: Add permission check before discovering services
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        e.printStackTrace()
                        runOnUiThread { statusTextView.text = "Permission Error: Cannot discover services" }
                    }
                } else {
                    runOnUiThread { statusTextView.text = "BLUETOOTH_CONNECT permission needed for service discovery" }
                }
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread { statusTextView.text = "Disconnected" }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    pressureCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                    // Fix #2: Add permission check before setting characteristic notification
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        try {
                            gatt.setCharacteristicNotification(pressureCharacteristic, true)
                            runOnUiThread { statusTextView.text = "Connected to BLE" }
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                            runOnUiThread { statusTextView.text = "Permission Error: Cannot set notifications" }
                        }
                    } else {
                        runOnUiThread { statusTextView.text = "BLUETOOTH_CONNECT permission needed for notifications" }
                    }
                } else {
                    runOnUiThread { statusTextView.text = "Service not found" }
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.getStringValue(0)?.trim()
            val pressure = data?.toIntOrNull() ?: return
            runOnUiThread { updatePressureUI(pressure) }
        }
    }

    private fun sendCommand(command: String) {
        if (pressureCharacteristic == null || bluetoothGatt == null) {
            Toast.makeText(this, "BLE not connected", Toast.LENGTH_SHORT).show()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "BLUETOOTH_CONNECT permission not granted", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            pressureCharacteristic!!.setValue(command)
            bluetoothGatt!!.writeCharacteristic(pressureCharacteristic)
        } catch (e: SecurityException) {
            e.printStackTrace()
            runOnUiThread { statusTextView.text = "SecurityException: Permission required" }
        }
    }

    private fun updatePressureUI(pressure: Int) {
        pressureTextView.text = "Pressure: $pressure/10"
        pressureBar.progress = pressure * 10

        val color = when (pressure) {
            in 7..10 -> android.R.color.holo_red_light
            in 4..6 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_green_light
        }
        pressureBar.progressDrawable.setColorFilter(
            ContextCompat.getColor(this, color),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix #3: Add permission check before closing GATT
        if (bluetoothGatt != null && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                bluetoothGatt?.close()
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
}