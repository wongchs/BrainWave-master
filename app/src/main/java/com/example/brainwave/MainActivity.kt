package com.example.brainwave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.brainwave.ui.BluetoothReceiver
import com.example.brainwave.utils.requestBluetoothPermissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions(this)
        setContent {
            BluetoothReceiver(this)
        }
    }
}
