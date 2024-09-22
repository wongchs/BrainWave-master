package com.example.brainwave.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import java.io.IOException
import android.util.Log
import java.util.UUID

class BluetoothClient(private val context: Context, private val onMessageReceived: (String) -> Unit) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun connectToServer() {
        if (bluetoothAdapter == null) {
            onMessageReceived("Device doesn't support Bluetooth")
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            onMessageReceived("Bluetooth is not enabled")
            return
        }

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
        val serverDevice = pairedDevices?.find { it.name == "NBLK-WAX9X" } // Replace with your laptop's Bluetooth name

        serverDevice?.let { device ->
            try {
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()
                onMessageReceived("Connected to server")
                listenForData()
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Could not connect to server", e)
                onMessageReceived("Failed to connect: ${e.message}")
            }
        } ?: onMessageReceived("Server device not found. Make sure it's paired.")
    }

    private fun listenForData() {
        Thread {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = socket?.inputStream?.read(buffer)
                    bytes?.let {
                        if (it > 0) {
                            val receivedMessage = String(buffer, 0, it)
                            onMessageReceived(receivedMessage)
                        }
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Error reading data", e)
                    onMessageReceived("Connection lost: ${e.message}")
                    break
                }
            }
        }.start()
    }

    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing socket", e)
        }
    }
}
