package com.example.brainwave.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

data class EmergencyContact(
    val id: String = "", val name: String = "", val phoneNumber: String = ""
)

fun addEmergencyContact(userId: String, name: String, phoneNumber: String) {
    val db = FirebaseFirestore.getInstance()
    val contact = EmergencyContact(name = name, phoneNumber = phoneNumber)
    db.collection("users").document(userId).collection("emergencyContacts").add(contact)
        .addOnSuccessListener { documentReference ->
            Log.d("Firestore", "Emergency contact added with ID: ${documentReference.id}")
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Error adding emergency contact", e)
        }
}

fun removeEmergencyContact(userId: String, contactId: String) {
    val db = FirebaseFirestore.getInstance()
    db.collection("users").document(userId).collection("emergencyContacts").document(contactId)
        .delete().addOnSuccessListener {
            Log.d("Firestore", "Emergency contact successfully deleted!")
        }.addOnFailureListener { e ->
            Log.w("Firestore", "Error deleting emergency contact", e)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencyContactsScreen() {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    var contacts by remember { mutableStateOf<List<EmergencyContact>>(emptyList()) }
    var newContactName by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("users").document(currentUser.uid)
                .collection("emergencyContacts").addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("Firestore", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        contacts = snapshot.documents.map { doc ->
                            EmergencyContact(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                phoneNumber = doc.getString("phoneNumber") ?: ""
                            )
                        }
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(contacts) { contact ->
                EmergencyContactItem(
                    contact = contact,
                    onDelete = {
                        if (currentUser != null && contact.id.isNotEmpty()) {
                            removeEmergencyContact(currentUser.uid, contact.id)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = newContactName,
            onValueChange = { newContactName = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = newContactPhone,
            onValueChange = { newContactPhone = it },
            label = { Text("Phone Number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (currentUser != null && newContactName.isNotEmpty() && newContactPhone.isNotEmpty()) {
                    addEmergencyContact(currentUser.uid, newContactName, newContactPhone)
                    newContactName = ""
                    newContactPhone = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add Contact")
        }

    }
}

@Composable
fun EmergencyContactItem(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium)
                Text(contact.phoneNumber, style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete contact")
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Contact") },
            text = { Text("Are you sure you want to delete this contact?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDialog = false
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}