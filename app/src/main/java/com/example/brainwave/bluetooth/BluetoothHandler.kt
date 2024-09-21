package com.example.brainwave.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.example.brainwave.utils.arePermissionsGranted
import com.example.brainwave.utils.requiredPermissions
import java.io.IOException
import java.util.UUID

fun listenForBluetoothMessages(context: Context, onMessageReceived: (String) -> Unit) {
    if (!arePermissionsGranted(context, requiredPermissions)) {
        onMessageReceived("Bluetooth permissions not granted")
        return
    }

    val bluetoothManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    if (bluetoothAdapter == null) {
        onMessageReceived("Device doesn't support Bluetooth")
        return
    }

    val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    try {
        val serverSocket: BluetoothServerSocket? = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("MyApp", uuid)
        var shouldLoop = true
        while (shouldLoop) {
            try {
                val socket: BluetoothSocket? = serverSocket?.accept()
                socket?.let {
                    val inputStream = it.inputStream
                    val buffer = ByteArray(1024)

                    // Continuously read from the input stream
                    while (true) {
                        val bytes = inputStream.read(buffer)
                        if (bytes > 0) {
                            val incomingMessage = String(buffer, 0, bytes)
                            onMessageReceived(incomingMessage)
                        } else {
                            // Connection lost or closed
                            break
                        }
                    }
                    it.close()
                }
            } catch (e: IOException) {
                e.printStackTrace()
                onMessageReceived("Bluetooth connection error: ${e.message}")
            } catch (e: SecurityException) {
                e.printStackTrace()
                onMessageReceived("Bluetooth permission denied: ${e.message}")
            }
        }
        serverSocket?.close()
    } catch (e: IOException) {
        e.printStackTrace()
        onMessageReceived("Bluetooth server socket error: ${e.message}")
    } catch (e: SecurityException) {
        e.printStackTrace()
        onMessageReceived("Bluetooth permission denied: ${e.message}")
    }
}