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
import android.location.Location
import android.media.RingtoneManager
import com.example.brainwave.utils.LocationManager
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class BluetoothClient(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit,
    private val onSeizureDetected: (String, List<Float>, String) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val locationManager = LocationManager(context)
    private var lastKnownLocation: Location? = null

    init {
        locationManager.startLocationUpdates { location ->
            lastKnownLocation = location
        }
    }

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

                            if (isValidJson(messageBuffer.toString())) {
                                val jsonObject = JSONObject(messageBuffer.toString())
                                if (jsonObject.has("seizure_detected") && jsonObject.getBoolean("seizure_detected")) {
                                    val data = jsonObject.getJSONArray("data")
                                    val dataList = MutableList(data.length()) { data.getDouble(it).toFloat() }
                                    val timestamp = jsonObject.getString("timestamp")
                                    onSeizureDetected(timestamp, dataList, getLocation())
                                }
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

    private fun getLocation(): String {
        return lastKnownLocation?.let { location ->
            "Latitude: ${location.latitude}, Longitude: ${location.longitude}"
        } ?: "Location unavailable"
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
        var seizureCallback: ((String, List<Float>, String) -> Unit)? = null
        var isAppInForeground = false
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetoothClient = BluetoothClient(
            this,
            { message ->
                if (!isAppInForeground) {
                    updateNotification(message)
                }
                dataCallback?.invoke(message)
            },
            { timestamp, data, location ->
                handleSeizureDetection(timestamp, data, location)
            }
        )
    }

    private fun handleSeizureDetection(timestamp: String, data: List<Float>, location: String) {
        // Show push notification
        val notification = createSeizureNotification(timestamp)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)

        // Log data and save to Firestore
        logSeizureData(timestamp, data, location)

        // Invoke callback
        seizureCallback?.invoke(timestamp, data, location)
    }

    private fun createSeizureNotification(timestamp: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seizure Detected")
            .setContentText("A seizure was detected at $timestamp")
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    private fun logSeizureData(timestamp: String, data: List<Float>, location: String) {
        // Implement Firestore logging here
        // For example:
        val db = FirebaseFirestore.getInstance()
        val seizureData = hashMapOf(
            "timestamp" to timestamp,
            "eegData" to data,
            "location" to location
        )

        db.collection("seizures")
            .add(seizureData)
            .addOnSuccessListener { documentReference ->
                Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error adding document", e)
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
