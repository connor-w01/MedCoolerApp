package com.example.blereaderapp

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var notificationHelper: NotificationHelper
    private var lastNotificationTime: Long = 0
    private val NOTIFICATION_COOLDOWN = 30000L // 30 seconds between notifications
    private var isAboveThreshold = false // Tracks if we're currently above threshold

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }

            if (allGranted) {
                Log.d("Permissions", "All necessary permissions granted.")
                bluetoothHelper.startScanningForDevice()
            } else {
                Log.e("Permissions", "Some permissions were denied: $permissions")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothHelper = BluetoothHelper(this)
        notificationHelper = NotificationHelper(this)

        // Request all permissions automatically on launch
        requestAllPermissions()

        setContent {
            val viewModel: BleViewModel = viewModel()
            val value by bluetoothHelper.characteristicValue.collectAsState()
            val isConnected by bluetoothHelper.isConnected.collectAsState()

            // Convert the byte array to a float (Celsius)
            val celsiusValue = value?.let { bytes ->
                if (bytes.size >= 4) {
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).float
                } else {
                    null
                }
            }

            // State to track the temperature system (Celsius or Fahrenheit)
            var isCelsius by remember { mutableStateOf(true) }

            // Convert the temperature to the selected unit
            val displayValue = celsiusValue?.let { temp ->
                if (isCelsius) {
                    "%.2f°C".format(temp)
                } else {
                    val fahrenheitValue = (temp * 9 / 5) + 32
                    "%.2f°F".format(fahrenheitValue)
                }
            } ?: "No data"

            // Update the ViewModel and check for temperature alerts
            LaunchedEffect(value) {
                celsiusValue?.let { temp ->
                    viewModel.updateCharacteristicValue(temp)

                    // Check temperature threshold (46°F = ~7.78°C)
                    val threshold = if (isCelsius) 7.78f else 46f
                    val currentTemp = if (isCelsius) temp else (temp * 9/5) + 32

                    if (currentTemp > threshold) {
                        // Determine if this is a new alert or just an update
                        val isNewCrossing = !isAboveThreshold
                        val cooldownExpired = System.currentTimeMillis() - lastNotificationTime > NOTIFICATION_COOLDOWN

                        if (isNewCrossing || cooldownExpired) {
                            notificationHelper.showTemperatureAlert(
                                currentTemp = temp,
                                isCelsius = isCelsius,
                                shouldAlert = isNewCrossing || cooldownExpired
                            )
                            lastNotificationTime = System.currentTimeMillis()
                        }
                        isAboveThreshold = true
                    } else {
                        isAboveThreshold = false // Reset when below threshold
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                BLEReaderScreen(
                    connectToDevice = { bluetoothHelper.startScanningForDevice() },
                    isConnected = isConnected,
                    displayValue = displayValue,
                    isCelsius = isCelsius,
                    toggleTemperatureSystem = { isCelsius = !isCelsius }
                )
            }
        }
    }

    private fun requestAllPermissions() {
        Log.d("Permissions", "requestAllPermissions() called")

        val permissions = mutableListOf<String>().apply {
            // Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
            }

            // Location permission (required for BLE scanning on older devices)
            add(Manifest.permission.ACCESS_FINE_LOCATION)

            // Notification permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        Log.d("Permissions", "Requesting: ${permissions.joinToString()}")
        requestPermissionLauncher.launch(permissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothHelper.disconnect()
    }
}

@Composable
fun BLEReaderScreen(
    connectToDevice: () -> Unit,
    isConnected: Boolean,
    displayValue: String,
    isCelsius: Boolean,
    toggleTemperatureSystem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Med",
                color = Color.Red,
                fontSize = 32.sp,
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = "Cooler",
                color = Color.Blue,
                fontSize = 32.sp,
                textDecoration = TextDecoration.Underline
            )
        }

        Button(onClick = connectToDevice) {
            Text("Scan for MedCooler Device")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Connection Status: ${if (isConnected) "Connected" else "Disconnected"}",
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Temperature: $displayValue",
            color = Color(0xFF888888),
            fontSize = 24.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = toggleTemperatureSystem,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Toggle Temperature System")
        }
    }
}