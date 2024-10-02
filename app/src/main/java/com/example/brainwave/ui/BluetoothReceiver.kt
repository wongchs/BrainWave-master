package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainwave.utils.LocationManager
import org.json.JSONObject

@Composable
fun BluetoothReceiver(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?
) {
    var message by remember { mutableStateOf("Waiting for connection...") }
    var dataPoints by remember { mutableStateOf(List(100) { 0f }) }

    LaunchedEffect(receivedData) {
        if (receivedData.isNotEmpty()) {
            try {
                val jsonObject = JSONObject(receivedData)
                val jsonArray = jsonObject.getJSONArray("data")
                val newPoints = List(jsonArray.length()) { jsonArray.getDouble(it).toFloat() }
                dataPoints = (dataPoints.drop(newPoints.size) + newPoints).takeLast(100)
                message = "Connected"
            } catch (e: Exception) {
                e.printStackTrace()
                message = "Error: ${e.message}"
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (message == "Connected") Icons.Default.Check else Icons.Default.Clear,
                    contentDescription = "Bluetooth status",
                    tint = if (message == "Connected") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Status: $message",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "EEG Graph",
                style = MaterialTheme.typography.titleMedium
            )
            EEGGraph(dataPoints)

            seizureData?.let { (timestamp, data, locationData) ->
                Spacer(modifier = Modifier.height(16.dp))
                SeizureDetectionCard(timestamp, data, locationData)
            }
        }
    }
}

