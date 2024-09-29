package com.example.brainwave.bluetooth

import android.Manifest
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
import android.content.pm.PackageManager
import android.location.Location
import android.media.RingtoneManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.example.brainwave.ui.EmergencyContact
import com.example.brainwave.utils.LocationManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

class BluetoothClient(
    private val context: Context,
    private val onMessageReceived: (String) -> Unit,
    private val onSeizureDetected: (String, List<Float>, LocationManager.LocationData?) -> Unit
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val locationManager = LocationManager(context)
    private var lastKnownLocationData: LocationManager.LocationData? = null

    init {
        locationManager.startLocationUpdates { locationData ->
            lastKnownLocationData = locationData
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
                                    onSeizureDetected(timestamp, dataList, lastKnownLocationData)
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

    private fun getLocationString(): String {
        return lastKnownLocationData?.let { locationData ->
            "Latitude: ${locationData.location.latitude}, " +
                    "Longitude: ${locationData.location.longitude}, " +
                    "Address: ${locationData.address}"
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
        var seizureCallback: ((String, List<Float>, LocationManager.LocationData?) -> Unit)? = null
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

    private fun handleSeizureDetection(timestamp: String, data: List<Float>, locationData: LocationManager.LocationData?) {
        // Show push notification
        val notification = createSeizureNotification(timestamp, locationData)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(2, notification)

        // Log data and save to Firestore
        logSeizureData(timestamp, data, locationData)

        // Invoke callback
        seizureCallback?.invoke(timestamp, data, locationData)

        sendSMSNotifications(timestamp, locationData)
    }

    private fun createSeizureNotification(timestamp: String, locationData: LocationManager.LocationData?): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val locationString = locationData?.let {
            "Location: ${it.location.latitude}, ${it.location.longitude}\nAddress: ${it.address}"
        } ?: "Location unavailable"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Seizure Detected")
            .setContentText("A seizure was detected at $timestamp")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("A seizure was detected at $timestamp\n$locationString"))
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    private fun logSeizureData(timestamp: String, data: List<Float>, locationData: LocationManager.LocationData?) {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser != null) {
            val seizureData = hashMapOf(
                "userId" to currentUser.uid,
                "timestamp" to timestamp,
                "eegData" to data,
                "latitude" to locationData?.location?.latitude,
                "longitude" to locationData?.location?.longitude,
                "address" to locationData?.address
            )

            db.collection("seizures")
                .add(seizureData)
                .addOnSuccessListener { documentReference ->
                    Log.d("Firestore", "DocumentSnapshot added with ID: ${documentReference.id}")
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Error adding document", e)
                }
        } else {
            Log.w("Firestore", "User not authenticated, seizure data not saved")
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

    private fun sendSMSNotifications(timestamp: String, locationData: LocationManager.LocationData?) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(currentUser.uid)
                .collection("emergencyContacts")
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        val contact = document.toObject(EmergencyContact::class.java)
                        sendSMS(contact.phoneNumber, createSMSMessage(timestamp, locationData))
                    }
                }
                .addOnFailureListener { exception ->
                    Log.w("Firestore", "Error getting emergency contacts", exception)
                }
        }
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
            try {
                Log.d("SMS", "Attempting to send SMS to $phoneNumber")
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Log.d("SMS", "SMS sent successfully to $phoneNumber")
            } catch (e: Exception) {
                Log.e("SMS", "Failed to send SMS to $phoneNumber", e)
            }
        } else {
            Log.e("SMS", "SMS permission not granted")
        }
    }

    private fun createSMSMessage(timestamp: String, locationData: LocationManager.LocationData?): String {
        val locationString = locationData?.let {
            "Location: ${it.location.latitude}, ${it.location.longitude}\nAddress: ${it.address}"
        } ?: "Location unavailable"

        return "Seizure detected at $timestamp\n$locationString"
    }
}
