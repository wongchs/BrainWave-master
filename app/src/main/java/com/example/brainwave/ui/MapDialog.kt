package com.example.brainwave.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat.getSystemService
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*


@Composable
fun MapDialog(latitude: Double?, longitude: Double?, address: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current

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
                    "Address:",
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = address ?: "Not available",
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                        .clickable(enabled = address != null) {
                            address?.let { copyToClipboard(context, it) }
                        }
                        .alpha(if (address != null) 1f else 0.6f)
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

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = getSystemService(context, ClipboardManager::class.java)
    val clip = ClipData.newPlainText("address", text)
    clipboard?.setPrimaryClip(clip)
    Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
}

@Composable
fun rememberMarkerState(
    position: LatLng = LatLng(0.0, 0.0)
): MarkerState = remember { MarkerState(position) }