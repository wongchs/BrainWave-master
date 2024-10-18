package com.example.brainwave.ui

import android.content.Context
import android.location.Location
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import org.json.JSONObject

@Composable
fun BluetoothReceiver(
    context: Context,
    receivedData: String,
) {
    var message by remember { mutableStateOf("Waiting for connection...") }
    var dataPoints by remember { mutableStateOf(List(100) { 0f }) }
    var seizureData by remember { mutableStateOf<Triple<String, List<Float>, LocationManager.LocationData?>?>(null) }

    val currentUser = FirebaseAuth.getInstance().currentUser
    val db = FirebaseFirestore.getInstance()

    LaunchedEffect(currentUser) {
        currentUser?.let { user ->
            db.collection("seizures")
                .whereEqualTo("userId", user.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val document = documents.documents[0]
                        val timestamp = document.getString("timestamp") ?: ""
                        val data = document.get("eegData") as? List<Float> ?: emptyList()
                        val latitude = document.getDouble("latitude")
                        val longitude = document.getDouble("longitude")
                        val address = document.getString("address")

                        val locationData = if (latitude != null && longitude != null) {
                            LocationManager.LocationData(
                                Location("").apply {
                                    this.latitude = latitude
                                    this.longitude = longitude
                                },
                                address ?: ""
                            )
                        } else null

                        seizureData = Triple(timestamp, data, locationData)
                    } else {
                        seizureData = null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("BluetoothReceiver", "Error getting seizure data", exception)
                    seizureData = null
                }
        }
    }

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
                message = when {
                    e.message?.contains("bluetooth", ignoreCase = true) == true -> "Bluetooth is not enabled. Please turn on Bluetooth to use this feature."
                    e.message?.contains("connection", ignoreCase = true) == true -> "Unable to connect. Please make sure the device is nearby and paired."
                    else -> "An error occurred. Please try again later."
                }
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

