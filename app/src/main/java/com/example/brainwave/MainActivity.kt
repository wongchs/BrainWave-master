package com.example.brainwave

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.io.InputStream
import java.util.*

class MainActivity : ComponentActivity() {

    private var bluetoothSocket: BluetoothSocket? = null
    private val serverMacAddress = "00:1A:7D:DA:71:13" // Replace with your server's MAC address
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val receivedData = mutableStateOf("")
    private val connectionStatus = mutableStateOf("")

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            startBluetoothOperations()
        } else {
            showPermissionDeniedMessage()
        }
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = {
                            Text("BrainWave Connector")
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            checkAndRequestPermissions()
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Connection Status: ${connectionStatus.value}")
                    Text("Received Data: ${receivedData.value}")
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        if (hasAllPermissions()) {
            startBluetoothOperations()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun hasAllPermissions(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startBluetoothOperations() {
        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not supported on this device", Toast.LENGTH_LONG).show()
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(this, "Please enable Bluetooth", Toast.LENGTH_LONG).show()
            return
        }
        connectToServer(bluetoothAdapter)
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Bluetooth permissions are required for this app to function properly", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("MissingPermission")
    private fun connectToServer(bluetoothAdapter: BluetoothAdapter?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(serverMacAddress)
                    ?: throw Exception("Bluetooth adapter is null")

                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Attempting to connect to: ${device.name} (${device.address})"
                }

                bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
                bluetoothSocket?.connect()

                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Connected to: ${device.name} (${device.address})"
                }

                receiveData()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Connection failed: ${e.message}"
                    Log.e("BluetoothConnection", "Connection error", e)
                }
            }
        }
    }

    private suspend fun receiveData() {
        val inputStream: InputStream = bluetoothSocket?.inputStream ?: run {
            connectionStatus.value = "Error: Bluetooth socket is null"
            return
        }
        val buffer = ByteArray(1024)

        while (true) {
            try {
                val bytes = inputStream.read(buffer)
                if (bytes == -1) {
                    withContext(Dispatchers.Main) {
                        connectionStatus.value = "Connection closed by server"
                    }
                    break
                }
                val receivedDataString = String(buffer, 0, bytes)
                try {
                    val jsonArray = JSONArray(receivedDataString)
                    withContext(Dispatchers.Main) {
                        receivedData.value = jsonArray.toString()
                    }
                } catch (e: JSONException) {
                    withContext(Dispatchers.Main) {
                        receivedData.value = "Received non-JSON data: $receivedDataString"
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Read failed: ${e.message}"
                    Log.e("BluetoothConnection", "Read error", e)
                }
                break
            }
        }

        // Close the socket after the loop ends
        withContext(Dispatchers.IO) {
            try {
                bluetoothSocket?.close()
            } catch (e: IOException) {
                Log.e("BluetoothConnection", "Error closing socket", e)
            }
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        bluetoothSocket?.close()
    }
}