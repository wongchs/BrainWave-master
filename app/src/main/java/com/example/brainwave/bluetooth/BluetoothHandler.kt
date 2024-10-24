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
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
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
    private var isConnected = false
    private var shouldReconnect = true
    private var reconnectThread: Thread? = null

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
        val serverDevice = pairedDevices?.find { it.name == "NBLK-WAX9X" }

        serverDevice?.let { device ->
            try {
                socket?.close()
                socket = device.createRfcommSocketToServiceRecord(uuid)
                socket?.connect()
                isConnected = true
                shouldReconnect = true
                onMessageReceived("Connected to server")
                listenForData()
            } catch (e: IOException) {
                Log.e("BluetoothClient", "Could not connect to server", e)
                onMessageReceived("Failed to connect: ${e.message}")
                isConnected = false
                startReconnectionThread()
            }
        } ?: onMessageReceived("Server device not found. Make sure it's paired.")
    }

    private fun startReconnectionThread() {
        stopReconnectionThread()
        reconnectThread = Thread {
            while (shouldReconnect && !isConnected) {
                try {
                    Thread.sleep(5000)
                    if (shouldReconnect && !isConnected) {
                        connectToServer()
                    }
                } catch (e: InterruptedException) {
                    break
                } catch (e: Exception) {
                    Log.e("BluetoothClient", "Reconnection attempt failed", e)
                }
            }
        }.apply { start() }
    }

    private fun stopReconnectionThread() {
        reconnectThread?.interrupt()
        reconnectThread = null
    }

    fun disconnect() {
        shouldReconnect = false
        stopReconnectionThread()
        try {
            socket?.close()
            isConnected = false
        } catch (e: IOException) {
            Log.e("BluetoothClient", "Error closing socket", e)
        }
    }

    fun isConnected(): Boolean {
        return isConnected
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
            while (shouldReconnect) {
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
                                    val dataList =
                                        MutableList(data.length()) { data.getDouble(it).toFloat() }
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
                    isConnected = false
                    if (shouldReconnect) {
                        startReconnectionThread()
                    }
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
}

class BluetoothService : Service() {
    private lateinit var bluetoothClient: BluetoothClient
    private val NOTIFICATION_ID = 1
    private val CHANNEL_ID_SEIZURE = "SeizureAlertChannel"
    private val CHANNEL_ID_EEG = "EEGDataChannel"

    companion object {
        var dataCallback: ((String) -> Unit)? = null
        var seizureCallback: ((String, List<Float>, LocationManager.LocationData?) -> Unit)? = null
        var isAppInForeground = false
        private lateinit var instance: BluetoothService

        fun refreshConnection() {
            if (::instance.isInitialized) {
                instance.reconnectToServer()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothClient.disconnect()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        startService(restartServiceIntent)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createSeizureAlertChannel()
        createEEGDataChannel()
        bluetoothClient = BluetoothClient(
            this,
            { message ->
                if (!isAppInForeground) {
                    updateNotification(formatMessageForNotification(message))
                }
                dataCallback?.invoke(message)
            },
            { timestamp, data, location ->
                handleSeizureDetection(timestamp, data, location)
            }
        )
    }

    private fun formatMessageForNotification(message: String): String {
        return when {
            message.contains("Device doesn't support Bluetooth") ->
                "Bluetooth not supported on this device"
            message.contains("Bluetooth is not enabled") ->
                "Please enable Bluetooth to use this feature"
            message.contains("Could not connect to server") ->
                "Unable to connect to the EEG device. Please check if it's turned on and nearby"
            message.contains("Server device not found") ->
                "EEG device not found. Please make sure it's paired with your phone"
            message.contains("Connection lost") ->
                "Connection to EEG device lost. Attempting to reconnect..."
            message.contains("Connected to server") ->
                "Connected to EEG device successfully"
            message.contains("read failed") || message.contains("socket might closed or timeout") ->
                "Connection issue detected. Please check your EEG device"
            else -> message
        }
    }

    private fun handleSeizureDetection(
        timestamp: String,
        data: List<Float>,
        locationData: LocationManager.LocationData?
    ) {
        // Log data and save to Firestore
        logSeizureData(timestamp, data, locationData) { seizureId ->
            // Show push notification with the seizure ID
            val notification = createSeizureNotification(timestamp, locationData, seizureId)
            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(2, notification)

            sendSMSNotifications(timestamp, locationData)

            // Invoke callback
            seizureCallback?.invoke(timestamp, data, locationData)
        }
    }

    private fun createSeizureNotification(
        timestamp: String,
        locationData: LocationManager.LocationData?,
        seizureId: String
    ): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("NOTIFICATION_TYPE", "SEIZURE_DETECTED")
            putExtra("SEIZURE_ID", seizureId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val locationString = locationData?.let {
            "Location: ${it.location.latitude}, ${it.location.longitude}\nAddress: ${it.address}"
        } ?: "Location unavailable"

        val vibrationPattern = longArrayOf(0, 500, 200, 500)

        return NotificationCompat.Builder(this, CHANNEL_ID_SEIZURE)
            .setContentTitle("Seizure Detected")
            .setContentText("A seizure was detected at $timestamp")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("A seizure was detected at $timestamp\n$locationString")
            )
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(vibrationPattern)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun logSeizureData(
        timestamp: String,
        data: List<Float>,
        locationData: LocationManager.LocationData?,
        onComplete: (String) -> Unit
    ) {
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
                    onComplete(documentReference.id)
                }
                .addOnFailureListener { e ->
                    Log.w("Firestore", "Error adding document", e)
                    onComplete("") // Call with empty string in case of failure
                }
        } else {
            Log.w("Firestore", "User not authenticated, seizure data not saved")
            onComplete("") // Call with empty string if user is not authenticated
        }
    }

    private fun reconnectToServer() {
        if (!bluetoothClient.isConnected()) {
            bluetoothClient.disconnect() // Ensure any existing connection is closed
            bluetoothClient.connectToServer()
        } else {
            val message = "Already connected to EEG device"
            dataCallback?.invoke(message)
            if (!isAppInForeground) {
                updateNotification(message)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("EEG Monitoring Service Running")
        startForeground(NOTIFICATION_ID, notification)

        bluetoothClient.connectToServer()

        return START_STICKY
    }

    private fun createSeizureAlertChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Seizure Alert Channel"
            val descriptionText = "Channel for Seizure Alert notifications"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID_SEIZURE, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createEEGDataChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "EEG Data Channel"
            val descriptionText = "Channel for EEG Data notifications"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID_EEG, name, importance).apply {
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

        return NotificationCompat.Builder(this, CHANNEL_ID_EEG)
            .setContentTitle("EEG Monitor")
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

    private fun sendSMSNotifications(
        timestamp: String,
        locationData: LocationManager.LocationData?
    ) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseFirestore.getInstance()
                .collection("users").document(currentUser.uid)
                .collection("emergencyContacts")
                .get()
                .addOnSuccessListener { documents ->
                    for (document in documents) {
                        val contact = document.toObject(EmergencyContact::class.java)
                        val message = createSMSMessage(timestamp, locationData)
                        sendSMS(contact.phoneNumber, message)
                    }
                    Log.d("SMS", "Attempted to send SMS notifications for seizure at $timestamp")
                }
                .addOnFailureListener { exception ->
                    Log.w("Firestore", "Error getting emergency contacts", exception)
                }
        } else {
            Log.w("SMS", "User not authenticated, cannot send SMS notifications")
        }
    }


    private fun createSMSMessage(
        timestamp: String,
        locationData: LocationManager.LocationData?
    ): String {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userName = currentUser?.displayName ?: "Unknown User"
        val baseMessage = "Seizure detected for $userName at $timestamp"
        val locationString = locationData?.let {
            "\nLocation: ${it.location.latitude}, ${it.location.longitude}" +
                    "\nAddress: ${it.address}"
        } ?: "\nLocation information unavailable"

        return baseMessage + locationString
    }


    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            Log.d("SMS", "Attempting to send SMS to $phoneNumber")
            Log.d("SMS", "Message content: $message")
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Split the message if it's too long
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)

            Log.d("SMS", "SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Log.e("SMS", "Failed to send SMS to $phoneNumber", e)
            e.printStackTrace()
        }
    }
}
