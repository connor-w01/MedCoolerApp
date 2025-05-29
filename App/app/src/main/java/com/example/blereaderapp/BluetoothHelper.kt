
package com.example.blereaderapp

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.BluetoothLeScanner
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class BluetoothHelper(private val context: Context) {

    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothGatt: BluetoothGatt? = null

    private val _characteristicValue = MutableStateFlow<ByteArray?>(null)
    val characteristicValue: StateFlow<ByteArray?> get() = _characteristicValue

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> get() = _isConnected

    private var characteristic: BluetoothGattCharacteristic? = null
    private val handler = Handler(Looper.getMainLooper())
    private val readRunnable = object : Runnable {
        override fun run() {
            readCharacteristic()
            handler.postDelayed(this, 2000)
        }
    }

    // BLE Scanning variables
    private val bluetoothScanner: BluetoothLeScanner? by lazy {
        bluetoothAdapter?.bluetoothLeScanner
    }
    private var scanCallback: ScanCallback? = null
    private var isScanning = false
    private val SCAN_TIMEOUT_MS = 10000L // 10 seconds
    private val scanTimeoutHandler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun startScanningForDevice() {
        if (isScanning) return

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                result.device?.let { device ->
                    if (device.name == "MedCooler_ESP32") {
                        Log.d("BLE", "Found MedCooler_ESP32 device: ${device.address}")
                        stopScanning()
                        connectToDevice(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e("BLE", "Scan failed with error: $errorCode")
                _isConnected.value = false
                isScanning = false
            }
        }

        bluetoothScanner?.let { scanner ->
            try {
                isScanning = true
                scanner.startScan(scanCallback)
                Log.d("BLE", "Started scanning for MedCooler_ESP32")

                // Set scan timeout
                scanTimeoutHandler.postDelayed({
                    if (isScanning) {
                        stopScanning()
                        Log.d("BLE", "Scan timed out - device not found")
                    }
                }, SCAN_TIMEOUT_MS)
            } catch (e: Exception) {
                Log.e("BLE", "Failed to start scan", e)
                isScanning = false
            }
        } ?: run {
            Log.e("BLE", "Cannot start scanning, BluetoothAdapter not initialized")
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanning() {
        scanCallback?.let { callback ->
            bluetoothScanner?.stopScan(callback)
            scanTimeoutHandler.removeCallbacksAndMessages(null)
            isScanning = false
            Log.d("BLE", "Stopped scanning")
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        Log.d("BLE", "Attempting to connect to ${device.name ?: "unnamed device"} (${device.address})")
        bluetoothGatt?.close()
        bluetoothGatt = device.connectGatt(context, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("BLE", "Connected to GATT server")
                _isConnected.value = true
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d("BLE", "Disconnected from GATT server")
                _isConnected.value = false
                bluetoothGatt?.close()
                stopPeriodicReads()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID.fromString("1a5daf34-d0d9-4ee9-b8cd-c773aa936bb6"))
                characteristic = service?.getCharacteristic(UUID.fromString("cba1d466-344c-4be3-ab3f-189f80dd7518"))
                startPeriodicReads()
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val value = characteristic.value
                _characteristicValue.value = value
                val result = value?.toString(Charsets.UTF_8)
                Log.d("BLE", "Characteristic Read: $result")
            }
        }
    }

    private fun startPeriodicReads() {
        handler.post(readRunnable)
    }

    private fun stopPeriodicReads() {
        handler.removeCallbacks(readRunnable)
    }

    @SuppressLint("MissingPermission")
    private fun readCharacteristic() {
        bluetoothGatt?.let { gatt ->
            characteristic?.let { char ->
                gatt.readCharacteristic(char)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScanning()
        bluetoothGatt?.disconnect()
        stopPeriodicReads()
    }
}

