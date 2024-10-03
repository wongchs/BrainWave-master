package com.example.brainwave.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(navController: NavHostController, onLogout: () -> Unit) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    TopAppBar(
        title = {
            Text(
                when (currentRoute) {
                    "home" -> "EpiGuard"
                    "history" -> "Seizure History"
                    "contacts" -> "Emergency Contacts"
                    "profile" -> "Profile"
                    else -> "EpiGuard"
                }
            )
        },
        actions = {
            IconButton(onClick = onLogout) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
            }
        }
    )
}
