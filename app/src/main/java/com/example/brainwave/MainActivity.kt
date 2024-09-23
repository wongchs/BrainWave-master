package com.example.brainwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.brainwave.ui.BluetoothReceiver
import com.example.brainwave.utils.requestBluetoothPermissions
import android.content.Intent
import android.os.Build
import androidx.compose.runtime.mutableStateOf
import com.example.brainwave.bluetooth.BluetoothService

class MainActivity : ComponentActivity() {
    private val receivedData = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions(this)
        BluetoothService.dataCallback = { data ->
            receivedData.value = data
        }

        setContent {
            BluetoothReceiver(this, receivedData.value)
        }
        startBluetoothService()
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
