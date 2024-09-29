package com.example.brainwave.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun SeizureHistoryScreen(onBackClick: () -> Unit) {
    val seizures = remember { mutableStateOf<List<SeizureEvent>>(emptyList()) }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val db = Firebase.firestore
    val currentUser = FirebaseAuth.getInstance().currentUser

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("seizures")
                .whereEqualTo("userId", currentUser.uid)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    seizures.value = result.mapNotNull { document ->
                        document.toObject(SeizureEvent::class.java)
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
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
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
                    SeizureEventItem(seizure)
                }
            }
        }
    }
}

@Composable
fun SeizureEventItem(seizure: SeizureEvent) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Timestamp: ${seizure.timestamp}", style = MaterialTheme.typography.h6)
            Text("Latitude: ${seizure.latitude}", style = MaterialTheme.typography.body1)
            Text("Longitude: ${seizure.longitude}", style = MaterialTheme.typography.body1)
            Text("Address: ${seizure.address}", style = MaterialTheme.typography.body1)
            Text("EEG Data: ${seizure.eegData?.take(10)}...", style = MaterialTheme.typography.body2)
        }
    }
}

data class SeizureEvent(
    val timestamp: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val address: String? = null,
    val eegData: List<Float>? = null
)


