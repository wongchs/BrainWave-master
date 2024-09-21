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
import org.json.JSONArray
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    var dataPoints by remember { mutableStateOf(List(100) { 0f }) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (arePermissionsGranted(context, requiredPermissions)) {
            coroutineScope.launch(Dispatchers.IO) {
                listenForBluetoothMessages(context) { receivedMessage ->
                    try {
                        val jsonArray = JSONArray(receivedMessage)
                        val newPoints = List(10) { jsonArray.getDouble(it).toFloat() }
                        dataPoints = (dataPoints.drop(10) + newPoints).takeLast(100)
                        message = "Last received: ${newPoints.joinToString(", ")}"
                    } catch (e: Exception) {
                        e.printStackTrace()
                        message = "Error: ${e.message}"
                    }
                }
            }
        } else {
            message = "Bluetooth permissions not granted"
        }
    }

    Column {
        Text("Received Message: $message")
        Text("EEG Graph")
        EEGGraph(dataPoints)
    }
}

@Composable
fun EEGGraph(dataPoints: List<Float>) {
    val maxValue = dataPoints.maxOrNull() ?: 1f
    val minValue = dataPoints.minOrNull() ?: 0f

    Canvas(modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .padding(16.dp)
    ) {
        val width = size.width
        val height = size.height
        val pointWidth = width / (dataPoints.size - 1)
        val valueRange = (maxValue - minValue).coerceAtLeast(1f)

        dataPoints.forEachIndexed { index, value ->
            if (index < dataPoints.size - 1) {
                val startX = index * pointWidth
                val startY = height - ((value - minValue) / valueRange * height)
                val endX = (index + 1) * pointWidth
                val endY = height - ((dataPoints[index + 1] - minValue) / valueRange * height)

                drawLine(
                    color = Color.Blue,
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
            }
        }
    }
}

private fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
    return requiredPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

private fun listenForBluetoothMessages(context: Context, onMessageReceived: (String) -> Unit) {
    if (!arePermissionsGranted(context, requiredPermissions)) {
        onMessageReceived("Bluetooth permissions not granted")
        return
    }

    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    if (bluetoothAdapter == null) {
        onMessageReceived("Device doesn't support Bluetooth")
        return
    }

    val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    try {
        val serverSocket: BluetoothServerSocket? = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("MyApp", uuid)
        var shouldLoop = true
        while (shouldLoop) {
            try {
                val socket: BluetoothSocket? = serverSocket?.accept()
                socket?.let {
                    val inputStream = it.inputStream
                    val buffer = ByteArray(1024)

                    // Continuously read from the input stream
                    while (true) {
                        val bytes = inputStream.read(buffer)
                        if (bytes > 0) {
                            val incomingMessage = String(buffer, 0, bytes)
                            onMessageReceived(incomingMessage)
                        } else {
                            // Connection lost or closed
                            break
                        }
                    }
                    it.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                onMessageReceived("Bluetooth connection error: ${e.message}")
            } catch (e: SecurityException) {
                e.printStackTrace()
                onMessageReceived("Bluetooth permission denied: ${e.message}")
            }
        }
        serverSocket?.close()
    } catch (e: IOException) {
        e.printStackTrace()
        onMessageReceived("Bluetooth server socket error: ${e.message}")
    } catch (e: SecurityException) {
        e.printStackTrace()
        onMessageReceived("Bluetooth permission denied: ${e.message}")
    }
}