package com.example.brainwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.brainwave.ui.BluetoothReceiver
import com.example.brainwave.utils.requestBluetoothPermissions
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.brainwave.bluetooth.BluetoothService
import com.example.brainwave.ui.AuthScreen
import com.example.brainwave.ui.EditProfileScreen
import com.example.brainwave.ui.EmergencyContactsScreen
import com.example.brainwave.ui.MainScreen
import com.example.brainwave.ui.SeizureDetailScreen
import com.example.brainwave.ui.SeizureEvent
import com.example.brainwave.ui.SeizureHistoryScreen
import com.example.brainwave.ui.User
import com.example.brainwave.utils.LocationManager
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private val receivedData = mutableStateOf("")
    private val seizureData =
        mutableStateOf<Triple<String, List<Float>, LocationManager.LocationData?>?>(null)
    private val db by lazy { Firebase.firestore }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val currentUser = mutableStateOf<FirebaseUser?>(null)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startBluetoothService()
            } else {
                Toast.makeText(
                    this,
                    "Permissions are required for the app to function properly",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        requestRequiredPermissions()

        if (arePermissionsGranted(this, requiredPermissions)) {
            startBluetoothService()
        } else {
            requestPermissionLauncher.launch(requiredPermissions)
        }

        BluetoothService.dataCallback = { data ->
            receivedData.value = data
        }
        BluetoothService.seizureCallback = { timestamp, data, location ->
            seizureData.value = Triple(timestamp, data, location)
        }

        setContent {
            val navController = rememberNavController()

            NavHost(navController = navController, startDestination = "auth") {
                composable("auth") {
                    if (currentUser.value != null) {
                        LaunchedEffect(Unit) {
                            navController.navigate("main") {
                                popUpTo("auth") { inclusive = true }
                            }
                        }
                    } else {
                        AuthScreen(
                            onAuthSuccess = {
                                navController.navigate("main") {
                                    popUpTo("auth") { inclusive = true }
                                }
                            }
                        )
                    }
                }
                composable("main") {
                    MainScreen(
                        navController = navController,
                        context = this@MainActivity,
                        receivedData = receivedData.value,
                        seizureData = seizureData.value,
                        onLogout = {
                            auth.signOut()
                            currentUser.value = null
                            navController.navigate("auth") {
                                popUpTo("main") { inclusive = true }
                            }
                        }
                    )
                }

                composable("history") {
                    SeizureHistoryScreen(
                        onBackClick = { navController.navigateUp() },
                        onSeizureClick = { seizure ->
                            navController.navigate("seizure_detail/${seizure.id}")
                        }
                    )
                }

                composable(
                    "seizure_detail/{seizureId}",
                    arguments = listOf(navArgument("seizureId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val seizureId = backStackEntry.arguments?.getString("seizureId")
                    var seizure by remember { mutableStateOf<SeizureEvent?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }

                    LaunchedEffect(seizureId) {
                        if (seizureId != null) {
                            val db = Firebase.firestore
                            db.collection("seizures").document(seizureId).get()
                                .addOnSuccessListener { document ->
                                    if (document != null && document.exists()) {
                                        seizure = document.toObject(SeizureEvent::class.java)
                                            ?.copy(id = document.id)
                                        isLoading = false
                                    } else {
                                        errorMessage = "Seizure not found"
                                        isLoading = false
                                    }
                                }
                                .addOnFailureListener { e ->
                                    errorMessage = "Error loading seizure: ${e.message}"
                                    isLoading = false
                                }
                        } else {
                            errorMessage = "Invalid seizure ID"
                            isLoading = false
                        }
                    }

                    when {
                        isLoading -> {
                            CircularProgressIndicator()
                        }

                        errorMessage != null -> {
                            Text(errorMessage!!, color = Color.Red)
                        }

                        seizure != null -> {
                            SeizureDetailScreen(
                                seizure = seizure!!,
                                onBackClick = { navController.navigateUp() }
                            )
                        }
                    }

                }

                composable("edit_profile") {
                    var isLoading by remember { mutableStateOf(false) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    var successMessage by remember { mutableStateOf<String?>(null) }

                    EditProfileScreen(
                        user = currentUser.value?.let { firebaseUser ->
                            User(
                                firebaseUser.uid,
                                firebaseUser.email ?: "",
                                firebaseUser.displayName ?: ""
                            )
                        } ?: User(),
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
                            currentUser.value?.updateProfile(profileUpdates)
                                ?.addOnCompleteListener { task ->
                                    isLoading = false
                                    if (task.isSuccessful) {
                                        currentUser.value = auth.currentUser
                                        successMessage = "Profile updated successfully"
                                    } else {
                                        errorMessage =
                                            task.exception?.message ?: "Failed to update profile"
                                    }
                                }
                        },
                        onBackClick = { navController.navigateUp() }
                    )
                }

                auth.addAuthStateListener { firebaseAuth ->
                    currentUser.value = firebaseAuth.currentUser
                }
            }
        }
    }

    private fun startBluetoothService() {
        val serviceIntent = Intent(this, BluetoothService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        BluetoothService.isAppInForeground = true
    }

    override fun onPause() {
        super.onPause()
        BluetoothService.isAppInForeground = false
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest)
        } else {
            startBluetoothService()
        }
    }

}

