package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.brainwave.bluetooth.listenForBluetoothMessages
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray

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