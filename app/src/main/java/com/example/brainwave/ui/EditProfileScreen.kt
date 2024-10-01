package com.example.brainwave.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color

data class User(
    val id: String = "",
    val email: String = "",
    val name: String = ""
)

@Composable
fun EditProfileScreen(
    user: User,
    isLoading: Boolean,
    errorMessage: String?,
    successMessage: String?,
    onSaveProfile: (User) -> Unit,
    onBackClick: () -> Unit
) {
    var name by remember { mutableStateOf(user.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onSaveProfile(user.copy(name = name)) },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text("Save Profile")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBackClick, enabled = !isLoading) {
            Text("Back")
        }

        Spacer(modifier = Modifier.height(16.dp))

        errorMessage?.let {
            Text(it, color = Color.Red)
        }

        successMessage?.let {
            Text(it, color = Color.Green)
        }
    }
}