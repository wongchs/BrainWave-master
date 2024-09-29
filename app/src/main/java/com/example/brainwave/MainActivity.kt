package com.example.brainwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.brainwave.ui.BluetoothReceiver
import com.example.brainwave.utils.requestBluetoothPermissions
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.brainwave.bluetooth.BluetoothService
import com.example.brainwave.ui.MainScreen
import com.example.brainwave.ui.SeizureHistoryScreen
import com.example.brainwave.utils.LocationManager
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private val receivedData = mutableStateOf("")
    private val seizureData = mutableStateOf<Triple<String, List<Float>, LocationManager.LocationData?>?>(null)
    private val db by lazy { Firebase.firestore }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                startBluetoothService()
            } else {
                Toast.makeText(this, "Permissions are required for the app to function properly", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

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
            NavHost(navController = navController, startDestination = "main") {
                composable("main") {
                    MainScreen(
                        context = this@MainActivity,
                        receivedData = receivedData.value,
                        seizureData = seizureData.value,
                        onViewHistoryClick = { navController.navigate("history") }
                    )
                }
                composable("history") {
                    SeizureHistoryScreen(onBackClick = { navController.navigateUp() })
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
}
