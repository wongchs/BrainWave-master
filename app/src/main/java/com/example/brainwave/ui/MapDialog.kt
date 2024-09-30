package com.example.brainwave.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*

@Composable
fun MapDialog(latitude: Double?, longitude: Double?, address: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (latitude != null && longitude != null) {
                    val singapore = LatLng(latitude, longitude)
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(singapore, 15f)
                    }
                    val markerState = rememberMarkerState(position = singapore)

                    GoogleMap(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        cameraPositionState = cameraPositionState
                    ) {
                        Marker(
                            state = markerState,
                            title = "Seizure Location",
                            snippet = address
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Text(
                            "Location data not available",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Text(
                    "Latitude: $latitude, Longitude: $longitude",
                    modifier = Modifier.padding(8.dp)
                )
                Text(
                    "Address: $address",
                    modifier = Modifier.padding(8.dp)
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun rememberMarkerState(
    position: LatLng = LatLng(0.0, 0.0)
): MarkerState = remember { MarkerState(position) }