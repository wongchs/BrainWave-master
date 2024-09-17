package com.example.brainwave

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState

val permissionList = mutableListOf<String>()
val sdkVersion = android.os.Build.VERSION.SDK_INT
val brainFlowManager = BrainFlowManager()
class MainActivity : ComponentActivity() {

    private val foundDevices = mutableStateOf(emptyList<BleDevice>())


    override fun onStop() {
        brainFlowManager.stopBrainFlow()
        super.onStop()
    }

    @SuppressLint("MissingPermission")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)

    override fun onCreate(savedInstanceState: Bundle?) {

        if (sdkVersion <= 30) {
            permissionList.add("android.permission.BLUETOOTH")
            permissionList.add("android.permission.BLUETOOTH_ADMIN")
        }

        permissionList.add("android.permission.INTERNET")
        permissionList.add("android.permission.ACCESS_NETWORK_STATE")
        permissionList.add("android.permission.ACCESS_FINE_LOCATION")

        if (sdkVersion > 30) {
            permissionList.add("android.permission.BLUETOOTH_CONNECT")
            permissionList.add("android.permission.BLUETOOTH_SCAN")
        }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {

            // Check if Bluetooth is available on the device
            val bluetoothAvailable =
                packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
            val bluetoothLEAvailable =
                packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

            val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
            val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
            val bleScanManager =
                BleScanManager(bluetoothManager, 10000, scanCallback = BleScanCallback({
                    val name = it?.device?.name
                    val address = it?.device?.address
                    if (address.isNullOrBlank()) return@BleScanCallback

                    if (!name.isNullOrBlank()) {
                        val device = BleDevice(name, address)
                        updateFoundDevices(device)
                    }
                }))

            //bleScanManager.beforeScanActions.add { btnStartScan.isEnabled = false }
            bleScanManager.beforeScanActions.add {
                foundDevices.value = emptyList()
                //adapter.notifyDataSetChanged()
            }
            //bleScanManager.afterScanActions.add { btnStartScan.isEnabled = true }

            var permissionRequest by remember { mutableStateOf(false) }
            var permissionGranted by remember { mutableStateOf(false) }
            val permissionState = rememberMultiplePermissionsState(permissionList)

            if (!permissionRequest) {
                RequestPermission(permissionState) { granted ->
                    permissionGranted = granted
                    permissionRequest = true
                    if (granted) {
                        bleScanManager.scanBleDevices()
                    }
                }
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            titleContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        title = {
                            Text("BrainWave Connector")
                        }
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            Toast.makeText(this, "Refreshing....", Toast.LENGTH_SHORT).show()
                            permissionState.launchMultiplePermissionRequest()
                            if (permissionState.allPermissionsGranted) {
                                permissionGranted = true
                                bleScanManager.scanBleDevices()
                            }
                            // viewModel.startScanning() // Uncomment if using ViewModel
                        },
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (!bluetoothLEAvailable || !bluetoothAvailable || bluetoothAdapter == null) {
                        Text("Bluetooth LE is not available on this device.")
                    } else if (!bluetoothAdapter.isEnabled || !permissionGranted) {
                        Text("App missing permission to work correctly!")
                    } else {
                        BleDeviceListContainer()
                    }
                }

            }
        }
    }

    private fun updateFoundDevices(device: BleDevice) {
        foundDevices.value = foundDevices.value.toMutableList().apply {
            if (!contains(device)) {
                Log.d("Bluetooth", "name: ${device.name}, address: ${device.address}")
                add(device)
            }
        }
    }

    @Composable
    fun BleDeviceListContainer() {
        val devices by remember { foundDevices }
        BleDeviceList(devices)
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RequestPermission(
    permissionState: MultiplePermissionsState,
    onPermissionStatusChanged: (Boolean) -> Unit
) {
    val openAlertDialog = remember { mutableStateOf(false) }

    Log.d("Permission", "Request Permission")
    if (!permissionState.allPermissionsGranted) {
        openAlertDialog.value = true
        openAlertDialog.value = true
        if (openAlertDialog.value) {
            ShowAlert(
                onDismissRequest = {
                    openAlertDialog.value = false
                    onPermissionStatusChanged(false)
                },
                onConfirmation = {
                    openAlertDialog.value = false
                    permissionState.launchMultiplePermissionRequest()
                    onPermissionStatusChanged(permissionState.allPermissionsGranted)
                },
                dialogTitle = "Request Permission",
                dialogText = "App missing permission to work correctly!",
                icon = Icons.Default.Warning
            )
        }
    } else {
        onPermissionStatusChanged(true) // Permission already granted
    }
}

@Composable
fun ShowAlert(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        icon = { Icon(icon, contentDescription = "Example Icon") },
        onDismissRequest = onDismissRequest,
        title = { Text(text = dialogTitle) },
        text = { Text(text = dialogText) },
        confirmButton = {
            TextButton(onClick = onConfirmation) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Dismiss")
            }
        }
    )
}
