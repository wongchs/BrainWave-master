package com.example.brainwave.ui

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.brainwave.utils.LocationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest

@Composable
fun MainApp(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?,
    onLogout: () -> Unit
) {
    val navController = rememberNavController()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val currentUser = FirebaseAuth.getInstance().currentUser
    val user = remember(currentUser) {
        currentUser?.let { User(it.uid, it.email ?: "", it.displayName ?: "") } ?: User()
    }

    Scaffold(
        topBar = { TopBar(navController, onLogout) },
        bottomBar = { BottomNavBar(navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("home") {
                HomeScreen(context, receivedData, seizureData)
            }
            composable("history") {
                SeizureHistoryScreen(
                    onBackClick = { navController.navigateUp() },
                    onSeizureClick = { seizure ->
                        navController.navigate("seizure_detail/${seizure.id}")
                    }
                )
            }
            composable("contacts") {
                EmergencyContactsScreen(onBackClick = { navController.navigateUp() })
            }
            composable("profile") {
                EditProfileScreen(
                    user = user,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onSaveProfile = { updatedUser ->
                        isLoading = true
                        errorMessage = null
                        successMessage = null

                        val profileUpdates = userProfileChangeRequest {
                            displayName = updatedUser.name
                        }
                        currentUser?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    successMessage = "Profile updated successfully"
                                } else {
                                    errorMessage = task.exception?.message ?: "Failed to update profile"
                                }
                            }
                    },
                    onBackClick = { navController.navigateUp() }
                )
            }
            // Add other necessary composables here
        }
    }
}