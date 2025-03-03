package com.example.cuff.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.data.Data
import java.util.*

class CuffBleManager(context: Context) : BleManager(context) {
    companion object {
        private const val TAG = "CuffBleManager"

        // Service and characteristic UUIDs
        val SERVICE_UUID: UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID: UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
    }

    private var pressureCharacteristic: BluetoothGattCharacteristic? = null
    private var pressureCallback: ((Int) -> Unit)? = null

    // Function to set the pressure callback
    fun setPressureCallback(callback: (Int) -> Unit) {
        pressureCallback = callback
    }

    // Send command to the device
    fun sendCommand(command: String): Boolean {
        return pressureCharacteristic?.let { characteristic ->
            writeCharacteristic(
                characteristic,
                Data(command.toByteArray())
            ).enqueue()
            Log.d(TAG, "Command sent: $command")
            true
        } ?: run {
            Log.e(TAG, "Characteristic not found")
            false
        }
    }

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        // Look for our service
        val service = gatt.getService(SERVICE_UUID)

        // If the service was found, look for our characteristic
        if (service != null) {
            val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)

            if (characteristic != null) {
                // Check if the characteristic has the required properties
                val properties = characteristic.properties
                val canRead = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                val canNotify = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val canWrite = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0

                if (canWrite) {
                    pressureCharacteristic = characteristic
                    return true
                }
            }
        }
        return false
    }

    override fun initialize() {
        // Enable notifications for the pressure characteristic
        pressureCharacteristic?.let { characteristic ->
            setNotificationCallback(characteristic).with { _, data ->
                if (data != null) {
                    // Parse the pressure value
                    val pressureString = data.value?.let { String(it).trim() } ?: ""
                    val pressure = pressureString.toIntOrNull()
                    if (pressure != null) {
                        pressureCallback?.invoke(pressure)
                    } else {
                        Log.e(TAG, "Invalid pressure data: $pressureString")
                    }
                }
            }

            // Enable notifications
            enableNotifications(characteristic).enqueue()
        }
    }

    override fun onServicesInvalidated() {
        // When services are invalidated (for example, when the device disconnects)
        pressureCharacteristic = null
    }
}