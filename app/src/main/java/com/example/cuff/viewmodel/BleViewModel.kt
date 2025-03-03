package com.example.cuff.viewmodel
import android.os.Build
import android.Manifest

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.cuff.ble.CuffBleManager
import no.nordicsemi.android.ble.ConnectRequest
import no.nordicsemi.android.ble.observer.ConnectionObserver
import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat
import no.nordicsemi.android.support.v18.scanner.ScanCallback
import no.nordicsemi.android.support.v18.scanner.ScanFilter
import no.nordicsemi.android.support.v18.scanner.ScanResult
import no.nordicsemi.android.support.v18.scanner.ScanSettings

class BleViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val bleManager = CuffBleManager(context)
    private val scanner = BluetoothLeScannerCompat.getScanner()

    private var isConnecting = false
    private var connectionRequest: ConnectRequest? = null

    // LiveData for UI updates
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState

    private val _pressureData = MutableLiveData<Int>()
    val pressureData: LiveData<Int> = _pressureData

    private val _scanState = MutableLiveData<ScanState>()
    val scanState: LiveData<ScanState> = _scanState

    private val _logMessage = MutableLiveData<String>()
    val logMessage: LiveData<String> = _logMessage

    private var isScanning = false
    private val SCAN_PERIOD: Long = 10000 // 10 seconds

    // Helper function to safely retrieve a device name
    @SuppressLint("MissingPermission")
    private fun getDeviceName(device: BluetoothDevice): String {
        return if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            device.name ?: "Unknown Device"
        } else {
            "Unknown Device"
        }
    }

    // Set up connection observer
    private val connectionObserver = object : ConnectionObserver {
        override fun onDeviceConnecting(device: BluetoothDevice) {
            _connectionState.postValue(ConnectionState.CONNECTING)
            log("Connecting to device...")
        }

        override fun onDeviceConnected(device: BluetoothDevice) {
            _connectionState.postValue(ConnectionState.CONNECTED)
            log("Connected to ${getDeviceName(device)}")
        }

        override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
            _connectionState.postValue(ConnectionState.DISCONNECTED)
            log("Failed to connect: $reason")
            isConnecting = false
            // Try to scan again after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                startScan()
            }, 1000)
        }

        override fun onDeviceReady(device: BluetoothDevice) {
            log("Device ready")
            _connectionState.postValue(ConnectionState.CONNECTED)
            _logMessage.postValue("Device ready")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {
            _connectionState.postValue(ConnectionState.DISCONNECTING)
            log("Disconnecting...")
        }

        override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
            _connectionState.postValue(ConnectionState.DISCONNECTED)
            log("Disconnected: $reason")
            // Try to scan again after a delay
            Handler(Looper.getMainLooper()).postDelayed({
                startScan()
            }, 1000)
        }
    }

    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _scanState.value = ScanState.IDLE

        // Set up pressure callback
        bleManager.setPressureCallback { pressure ->
            _pressureData.postValue(pressure)
        }

        // Set up connection observer
        bleManager.connectionObserver = connectionObserver
    }

    // Connection states
    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    // Scan states
    enum class ScanState {
        SCANNING,
        IDLE
    }

    // Get Bluetooth adapter
    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManager.adapter
    }

    // Check permissions for Bluetooth and Location (needed for BLE)
    private fun hasPermission(permission: String): Boolean {
        return ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // Start scanning for BLE devices
    fun startScan() {
        if (isScanning) return

        // Check permissions based on the Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) ||
                !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                log("Permissions not granted")
                return
            }
        } else {
            if (!hasPermission(Manifest.permission.BLUETOOTH) ||
                !hasPermission(Manifest.permission.BLUETOOTH_ADMIN) ||
                !hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                log("Permissions not granted")
                return
            }
        }

        _scanState.value = ScanState.SCANNING
        log("Starting scan...")

        try {
            val filters = listOf<ScanFilter>()
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            scanner.startScan(filters, settings, scanCallback)
            isScanning = true

            // Stop scan after delay
            Handler(Looper.getMainLooper()).postDelayed({
                stopScan()
            }, SCAN_PERIOD)
        } catch (e: Exception) {
            log("Scan error: ${e.message}")
            isScanning = false
            _scanState.value = ScanState.IDLE
        }
    }

    // Stop scanning
    fun stopScan() {
        if (!isScanning) return

        try {
            scanner.stopScan(scanCallback)
            isScanning = false
            _scanState.value = ScanState.IDLE
            log("Scan stopped")
        } catch (e: Exception) {
            log("Error stopping scan: ${e.message}")
        }
    }

    // Connect to a device
    private fun connect(device: BluetoothDevice) {
        if (isConnecting) return

        isConnecting = true
        _connectionState.value = ConnectionState.CONNECTING

        // Log the connection attempt using the helper function
        if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
            log("Connecting to ${getDeviceName(device)}...")
        } else {
            log("Bluetooth permission denied")
            return
        }

        try {
            // Check permissions before attempting connection
            if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                log("Bluetooth permission denied")
                return
            }

            connectionRequest = bleManager.connect(device)
                .retry(3, 100)
                .useAutoConnect(true)

            // Enqueue the connection request
            connectionRequest?.enqueue()

        } catch (e: SecurityException) {
            log("Permission error: ${e.message}")
            isConnecting = false
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            log("Connection error: ${e.message}")
            isConnecting = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    // Disconnect from device
    fun disconnect() {
        connectionRequest?.cancel()
        connectionRequest = null

        if (bleManager.isConnected) {
            _connectionState.value = ConnectionState.DISCONNECTING
            log("Disconnecting...")
            bleManager.disconnect().enqueue()
        }
    }

    // Send command to the device
    fun sendCommand(command: String): Boolean {
        if (!bleManager.isConnected) {
            log("Not connected")
            return false
        }

        return bleManager.sendCommand(command)
    }

    // Log messages and update UI
    private fun log(message: String) {
        Log.d("BleViewModel", message)
        _logMessage.postValue(message)
    }

    // Scan callback
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Use permission check before accessing the device name from scanRecord or device
            val name = if (hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                result.scanRecord?.deviceName ?: getDeviceName(result.device)
            } else {
                "Unknown Device"
            }

            if (name.contains("XIAO-BLE", ignoreCase = true)) {
                log("Found device: $name")
                stopScan()
                connect(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            _scanState.postValue(ScanState.IDLE)
            log("Scan failed with error code: $errorCode")
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnect()
    }
}
