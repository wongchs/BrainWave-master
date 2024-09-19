package com.example.brainwave

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

class MainActivity : ComponentActivity() {

    private var socket: Socket? = null
    private val serverIpAddress = "10.0.2.2"  // Replace with your server's IP address
    private val serverPort = 5000  // Should match the port in the Python script
    private val receivedData = mutableStateOf("")
    private val connectionStatus = mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        title = { Text("BrainWave Connector") }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { connectToServer() }
                    ) {
                        Text("Connect")
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

    private fun connectToServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Attempting to connect to: $serverIpAddress:$serverPort"
                }

                socket = Socket(serverIpAddress, serverPort)

                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Connected to: $serverIpAddress:$serverPort"
                }

                receiveData()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Connection failed: ${e.message}"
                    Log.e("SocketConnection", "Connection error", e)
                }
            }
        }
    }

    private suspend fun receiveData() {
        val inputStream = socket?.getInputStream() ?: run {
            connectionStatus.value = "Error: Socket is null"
            return
        }
        val reader = BufferedReader(InputStreamReader(inputStream))

        while (true) {
            try {
                val receivedDataString = reader.readLine()
                if (receivedDataString == null) {
                    withContext(Dispatchers.Main) {
                        connectionStatus.value = "Connection closed by server"
                    }
                    break
                }
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
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    connectionStatus.value = "Read failed: ${e.message}"
                    Log.e("SocketConnection", "Read error", e)
                }
                break
            }
        }

        withContext(Dispatchers.IO) {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e("SocketConnection", "Error closing socket", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
    }
}