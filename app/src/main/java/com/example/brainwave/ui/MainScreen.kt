package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.brainwave.utils.LocationManager

@Composable
fun MainScreen(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?,
    onViewHistoryClick: () -> Unit,
    onViewEmergencyContactsClick: () -> Unit,
    onEditProfileClick: () -> Unit,
    onLogout: () -> Unit
) {
    Column {
        BluetoothReceiver(context, receivedData, seizureData)

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onViewHistoryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("View Seizure History")
        }

        Button(
            onClick = onViewEmergencyContactsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Manage Emergency Contacts")
        }

        Button(
            onClick = onEditProfileClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Edit Profile")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text("Logout")
        }
    }
}