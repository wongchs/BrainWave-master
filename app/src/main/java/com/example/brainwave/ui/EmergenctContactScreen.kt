package com.example.brainwave.ui

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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

@Composable
fun EmergencyContactsScreen(
    onBackClick: () -> Unit
) {
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
        Text("Emergency Contacts", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        contacts.forEach { contact ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${contact.name}: ${contact.phoneNumber}",
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        if (currentUser != null && contact.id.isNotEmpty()) {
                            removeEmergencyContact(currentUser.uid, contact.id)
                        }
                    }
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete contact")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(value = newContactName,
            onValueChange = { newContactName = it },
            label = { Text("Name") })
        TextField(value = newContactPhone,
            onValueChange = { newContactPhone = it },
            label = { Text("Phone Number") })
        Button(onClick = {
            if (currentUser != null && newContactName.isNotEmpty() && newContactPhone.isNotEmpty()) {
                addEmergencyContact(currentUser.uid, newContactName, newContactPhone)
                newContactName = ""
                newContactPhone = ""
            }
        }) {
            Text("Add Contact")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackClick) {
            Text("Back")
        }
    }
}
