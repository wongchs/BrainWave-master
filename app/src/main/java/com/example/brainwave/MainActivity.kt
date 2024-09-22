package com.example.brainwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.brainwave.ui.BluetoothReceiver
import com.example.brainwave.utils.requestBluetoothPermissions
import android.content.Intent
import android.os.Build
import com.example.brainwave.bluetooth.BluetoothService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions(this)
        startBluetoothService()
        setContent {
            BluetoothReceiver(this)
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
}
