package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.brainwave.bluetooth.BluetoothClient
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun BluetoothReceiver(context: Context, receivedData: String) {
    var message by remember { mutableStateOf("Waiting for connection...") }
    var dataPoints by remember { mutableStateOf(List(100) { 0f }) }

    LaunchedEffect(receivedData) {
        if (receivedData.isNotEmpty()) {
            try {
                val jsonObject = JSONObject(receivedData)
                val jsonArray = jsonObject.getJSONArray("data")
                val newPoints = List(jsonArray.length()) { jsonArray.getDouble(it).toFloat() }
                dataPoints = (dataPoints.drop(newPoints.size) + newPoints).takeLast(100)
                message = "Last received: ${newPoints.joinToString(", ")}"
            } catch (e: Exception) {
                e.printStackTrace()
                message = "Error: ${e.message}"
            }
        }
    }

    Column {
        Text("Status: $message")
        Text("EEG Graph")
        EEGGraph(dataPoints)
    }
}