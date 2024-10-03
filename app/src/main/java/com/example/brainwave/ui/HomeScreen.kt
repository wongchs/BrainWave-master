package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainwave.utils.LocationManager

@Composable
fun HomeScreen(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BluetoothReceiver(context, receivedData, seizureData)
        Spacer(modifier = Modifier.height(16.dp))
        seizureData?.let { (timestamp, data, locationData) ->
            SeizureDetectionCard(timestamp, data, locationData)
        }
    }
}
