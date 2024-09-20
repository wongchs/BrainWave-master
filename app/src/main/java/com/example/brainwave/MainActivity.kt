package com.example.brainwave

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.util.*

private val requiredPermissions = arrayOf(
    Manifest.permission.BLUETOOTH,
    Manifest.permission.BLUETOOTH_ADMIN,
    Manifest.permission.ACCESS_FINE_LOCATION
)


class MainActivity : ComponentActivity() {
    private val PERMISSION_REQUEST_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()
        setContent {
            BluetoothReceiver(this)
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest, PERMISSION_REQUEST_CODE)
        }
    }
}

@Composable
fun BluetoothReceiver(context: Context) {
    var message by remember { mutableStateOf("Waiting for message...") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (arePermissionsGranted(context, requiredPermissions)) {
            coroutineScope.launch(Dispatchers.IO) {
                listenForBluetoothMessages(context) { receivedMessage ->
                    message = receivedMessage
                }
            }
        } else {
            message = "Bluetooth permissions not granted"
        }
    }

    Column {
        Text("Received Message: $message")
    }
}

private fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
    return requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun listenForBluetoothMessages(context: Context, onMessageReceived: (String) -> Unit) {
    if (!arePermissionsGranted(context, requiredPermissions)) {
        // Permissions not granted, handle this case (e.g., show a message to the user)
        onMessageReceived("Bluetooth permissions not granted")
        return
    }

    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    if (bluetoothAdapter == null) {
        // Device doesn't support Bluetooth
        onMessageReceived("Device doesn't support Bluetooth")
        return
    }

    val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SerialPortService ID

    try {
        val serverSocket: BluetoothServerSocket? = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("MyApp", uuid)
        var shouldLoop = true
        while (shouldLoop) {
            try {
                val socket: BluetoothSocket? = serverSocket?.accept()
                socket?.let {
                    val inputStream = it.inputStream
                    val buffer = ByteArray(1024)
                    val bytes = inputStream.read(buffer)
                    val incomingMessage = String(buffer, 0, bytes)
                    onMessageReceived(incomingMessage)
                    it.close()
                }
            } catch (e: IOException) {
                // Handle connection errors
                e.printStackTrace()
                shouldLoop = false
                onMessageReceived("Bluetooth connection error: ${e.message}")
            } catch (e: SecurityException) {
                // Handle permission denied error
                e.printStackTrace()
                shouldLoop = false
                onMessageReceived("Bluetooth permission denied: ${e.message}")
            }
        }
        serverSocket?.close()
    } catch (e: IOException) {
        // Handle server socket errors
        e.printStackTrace()
        onMessageReceived("Bluetooth server socket error: ${e.message}")
    } catch (e: SecurityException) {
        // Handle permission denied error
        e.printStackTrace()
        onMessageReceived("Bluetooth permission denied: ${e.message}")
    }
}