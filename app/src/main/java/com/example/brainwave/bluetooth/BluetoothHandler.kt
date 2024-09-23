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
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.brainwave.MainActivity
import android.R
import org.json.JSONObject

class BluetoothClient(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit
) {
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
        val serverDevice =
            pairedDevices?.find { it.name == "NBLK-WAX9X" } // Replace with your laptop's Bluetooth name

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

    private fun reconnectToServer() {
        Thread {
            while (true) {
                try {
                    connectToServer()
                    break
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Reconnection failed", e)
                    Thread.sleep(5000) // Wait 5 seconds before trying again
                }
            }
        }.start()
    }

    private val messageBuffer = StringBuilder()

    private fun listenForData() {
        Thread {
            val buffer = ByteArray(1024)
            while (true) {
                try {
                    val bytes = socket?.inputStream?.read(buffer)
                    bytes?.let {
                        if (it > 0) {
                            val receivedData = String(buffer, 0, it)
                            messageBuffer.append(receivedData)

                            // Check if we have a complete JSON object
                            if (isValidJson(messageBuffer.toString())) {
                                onMessageReceived(messageBuffer.toString())
                                messageBuffer.clear()
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e("BluetoothClient", "Error reading data", e)
                    onMessageReceived("Connection lost: ${e.message}")
                    reconnectToServer()
                    break
                }
            }
        }.start()
    }

    private fun isValidJson(json: String): Boolean {
        try {
            JSONObject(json)
            return true
        } catch (e: Exception) {
            return false
        }
    }



    fun disconnect() {
        try {
            socket?.close()
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing socket", e)
        }
    }
}

class BluetoothService : Service() {
    private lateinit var bluetoothClient: BluetoothClient
    private val CHANNEL_ID = "BluetoothServiceChannel"
    private val NOTIFICATION_ID = 1

    companion object {
        var dataCallback: ((String) -> Unit)? = null
        var isAppInForeground = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetoothClient = BluetoothClient(this) { message ->
            if (!isAppInForeground) {
                updateNotification(message)
            }
            dataCallback?.invoke(message)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("Bluetooth Service Running")
        startForeground(NOTIFICATION_ID, notification)

        bluetoothClient.connectToServer()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Bluetooth Service Channel"
            val descriptionText = "Channel for Bluetooth Service notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth Data")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setSound(null)
            .setVibrate(null)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(content: String) {
        if (!isAppInForeground) {
            val notification = createNotification(content)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
