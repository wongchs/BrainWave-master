package com.example.brainwave

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.example.brainwave.bluetooth.BluetoothService
import com.example.brainwave.ui.MainApp
import com.example.brainwave.utils.LocationManager
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
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
            MainApp(
                context = this@MainActivity,
                receivedData = receivedData.value,
                seizureData = seizureData.value,
                onLogout = {
                    auth.signOut()
                    currentUser.value = null
                }
            )
        }

        auth.addAuthStateListener { firebaseAuth ->
            currentUser.value = firebaseAuth.currentUser
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