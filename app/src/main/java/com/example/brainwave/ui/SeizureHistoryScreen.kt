package com.example.brainwave.ui

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import android.content.ContentValues
import android.provider.MediaStore
import android.os.Environment
import android.widget.Toast
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeizureHistoryScreen(onBackClick: () -> Unit, onSeizureClick: (SeizureEvent) -> Unit) {
    val seizures = remember { mutableStateOf<List<SeizureEvent>>(emptyList()) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val db = Firebase.firestore
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    var showShareDialog by remember { mutableStateOf(false) }
    var downloadedFileUri by remember { mutableStateOf<Uri?>(null) }

    fun downloadSeizureHistory() {
        if (seizures.value.isEmpty()) {
            Toast.makeText(context, "No seizure history to download", Toast.LENGTH_SHORT).show()
            return
        }

        val csvData = buildString {
            appendLine("ID,Timestamp,Latitude,Longitude,Address,EEG Data")
            seizures.value.forEach { seizure ->
                val eegDataString = seizure.eegData?.joinToString(";") ?: "N/A"
                appendLine("${seizure.id},${seizure.timestamp},${seizure.latitude},${seizure.longitude},\"${seizure.address}\",\"$eegDataString\"")
            }
        }

        val fileName = "seizure_history_${System.currentTimeMillis()}.csv"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(csvData.toByteArray())
            }
            downloadedFileUri = it
            showShareDialog = true
            Toast.makeText(context, "Seizure history downloaded as $fileName", Toast.LENGTH_LONG).show()
        } ?: Toast.makeText(context, "Failed to download seizure history", Toast.LENGTH_SHORT).show()
    }

    fun openFile() {
        downloadedFileUri?.let { uri ->
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open CSV file"))
        }
    }

    fun shareFile() {
        downloadedFileUri?.let { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share CSV file"))
        }
    }

    if (showShareDialog) {
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("File Downloaded") },
            text = { Text("Your seizure history has been downloaded. What would you like to do next?") },
            confirmButton = {
                TextButton(onClick = {
                    openFile()
                    showShareDialog = false
                }) {
                    Text("Open File")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    shareFile()
                    showShareDialog = false
                }) {
                    Text("Share File")
                }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("seizures")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    seizures.value = result.mapNotNull { document ->
                        document.toObject(SeizureEvent::class.java).copy(id = document.id)
                    }
                    errorMessage.value = null
                }
                .addOnFailureListener { exception ->
                    Log.w("SeizureHistory", "Error getting documents.", exception)
                    errorMessage.value = "Failed to load seizure history. Please try again later."
                }
        }
    }

    Column {
        TopAppBar(
            title = { Text("Seizure History") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = { downloadSeizureHistory() }) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Download Seizure History"
                    )
                }
            }
        )

        if (errorMessage.value != null) {
            Text(
                text = errorMessage.value!!,
                color = Color.Red,
                modifier = Modifier.padding(16.dp)
            )
        } else if (seizures.value.isEmpty()) {
            Text(
                text = "No seizure history found.",
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn {
                items(seizures.value) { seizure ->
                    SeizureEventItem(seizure, onClick = { onSeizureClick(seizure) })
                }
            }
        }
    }
}

@Composable
fun SeizureEventItem(seizure: SeizureEvent, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timestamp: ${seizure.timestamp}", style = MaterialTheme.typography.titleLarge)
            Text("Latitude: ${seizure.latitude}", style = MaterialTheme.typography.bodyLarge)
            Text("Longitude: ${seizure.longitude}", style = MaterialTheme.typography.bodyLarge)
            Text("Address: ${seizure.address}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeizureDetailScreen(seizure: SeizureEvent, onBackClick: () -> Unit) {
    var showMap by remember { mutableStateOf(false) }

    Column {
        TopAppBar(
            title = { Text("Seizure Detail") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timestamp: ${seizure.timestamp}", style = MaterialTheme.typography.titleLarge)
            Text("Latitude: ${seizure.latitude}", style = MaterialTheme.typography.bodyLarge)
            Text("Longitude: ${seizure.longitude}", style = MaterialTheme.typography.bodyLarge)

            Text(
                text = "Address: ${seizure.address}",
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .clickable { showMap = true }
                    .padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("EEG Data", style = MaterialTheme.typography.titleLarge)
            seizure.eegData?.let { eegData ->
                EEGGraph(eegData)
            } ?: Text("No EEG data available")
        }

        if (showMap) {
            MapDialog(
                latitude = seizure.latitude,
                longitude = seizure.longitude,
                address = seizure.address,
                onDismiss = { showMap = false }
            )
        }
    }
}

data class SeizureEvent(
    val id: String = "",
    val timestamp: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val eegData: List<Float>? = null
)