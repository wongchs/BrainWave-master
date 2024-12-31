package com.example.brainwave.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.brainwave.bluetooth.BluetoothService
import com.example.brainwave.utils.LocationManager
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    currentUser: FirebaseUser?,
    onLogout: () -> Unit,
    content: @Composable () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Home", "History", "Contacts", "Profile")
    var showLogoutDialog by remember { mutableStateOf(false) }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isAuthScreen = currentRoute == "auth"

    val currentScreenTitle = when (currentRoute) {
        "main" -> "Home"
        "history" -> "History"
        "emergency_contacts" -> "Emergency Contacts"
        "edit_profile" -> "Edit Profile"
        "seizure_detail/{seizureId}" -> "Seizure Details"
        else -> "EpiGuard"
    }

    val backCallback = remember {
        object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    currentUser == null && !isAuthScreen -> {
                        navController.navigate("auth") {
                            popUpTo(0) { inclusive = true }
                        }
                    }

                    navController.previousBackStackEntry != null -> {
                        navController.navigateUp()
                    }
                }
            }
        }
    }

    backCallback.isEnabled = currentUser == null || !isAuthScreen

    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        backDispatcher?.addCallback(backCallback)
        onDispose {
            backCallback.remove()
        }
    }

    Scaffold(
        topBar = {
            if (!isAuthScreen) {
                TopAppBar(
                    title = { Text(currentScreenTitle) },
                    navigationIcon = {
                        if (navController.previousBackStackEntry != null) {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showLogoutDialog = true }) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!isAuthScreen) {
                NavigationBar {
                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            icon = {
                                when (index) {
                                    0 -> Icon(Icons.Default.Home, contentDescription = title)
                                    1 -> Icon(Icons.Default.List, contentDescription = title)
                                    2 -> Icon(Icons.Default.Call, contentDescription = title)
                                    3 -> Icon(
                                        Icons.Default.AccountCircle,
                                        contentDescription = title
                                    )
                                }
                            },
                            label = { Text(title) },
                            selected = selectedTab == index,
                            onClick = {
                                if (currentUser != null) {
                                    selectedTab = index
                                    when (index) {
                                        0 -> navController.navigate("main") {
                                            popUpTo("main") { inclusive = true }
                                        }

                                        1 -> navController.navigate("history") {
                                            popUpTo("main")
                                        }

                                        2 -> navController.navigate("emergency_contacts") {
                                            popUpTo("main")
                                        }

                                        3 -> navController.navigate("edit_profile") {
                                            popUpTo("main")
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier.padding(
                top = if (isAuthScreen) 0.dp else padding.calculateTopPadding(),
                bottom = if (isAuthScreen) 0.dp else padding.calculateBottomPadding(),
                start = padding.calculateStartPadding(LocalLayoutDirection.current),
                end = padding.calculateEndPadding(LocalLayoutDirection.current)
            )
        ) {
            content()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Confirm Logout") },
            text = { Text("Are you sure you want to log out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("No")
                }
            }
        )
    }

}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun HomeScreen(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?,
    onDismissSeizure: () -> Unit
) {
    BluetoothEnablePrompt(context)
    val locationManager = remember { LocationManager(context) }
    LocationEnablePrompt(locationManager)

    seizureData?.let { data ->
        SeizureAlertOverlay(
            seizureData = data,
            onDismiss = onDismissSeizure
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BluetoothReceiver(
            context = context,
            receivedData = receivedData,
            onRefreshConnection = {
                BluetoothService.refreshConnection()
            }
        )
    }
}

@Composable
fun BluetoothEnablePrompt(context: Context) {
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var showPrompt by remember { mutableStateOf(!bluetoothAdapter?.isEnabled!! ?: false) }

    var showPermissionsDeniedDialog by remember { mutableStateOf(false) }
    var showEnableBluetoothDialog by remember { mutableStateOf(false) }

    // Check if Bluetooth is supported
    if (bluetoothAdapter == null) {
        Toast.makeText(context, "Bluetooth is not supported on this device", Toast.LENGTH_LONG)
            .show()
        return
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            if (bluetoothAdapter?.isEnabled == false) {
                showEnableBluetoothDialog = true
            }
        } else {
            showPermissionsDeniedDialog = true
        }
    }

    // Bluetooth enable launcher
    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            Toast.makeText(
                context,
                "Bluetooth is required for this app to function properly",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        checkBluetoothPermissions(
            context = context,
            onPermissionsGranted = {
                if (bluetoothAdapter?.isEnabled == false) {
                    showEnableBluetoothDialog = true
                }
            },
            onPermissionRequest = { permissions ->
                permissionLauncher.launch(permissions)
            }
        )
    }

    // Permission denied dialog
    if (showPermissionsDeniedDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionsDeniedDialog = false },
            title = { Text("Permissions Required") },
            text = { Text("Bluetooth permissions are required for this app to function properly. Please enable them in settings.") },
            confirmButton = {
                TextButton(onClick = { showPermissionsDeniedDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    if (showEnableBluetoothDialog) {
        AlertDialog(
            onDismissRequest = { showEnableBluetoothDialog = false },
            title = { Text("Enable Bluetooth") },
            text = { Text("Bluetooth is required for this app to function properly. Would you like to enable it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEnableBluetoothDialog = false
                        try {
                            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                            enableBluetoothLauncher.launch(enableBtIntent)
                        } catch (e: SecurityException) {
                            Toast.makeText(
                                context,
                                "Unable to enable Bluetooth. Please check permissions.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEnableBluetoothDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun checkBluetoothPermissions(
    context: Context,
    onPermissionsGranted: () -> Unit,
    onPermissionRequest: (Array<String>) -> Unit
) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN
        )
    }

    val hasPermissions = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    if (hasPermissions) {
        onPermissionsGranted()
    } else {
        onPermissionRequest(permissions)
    }
}


@Composable
fun LocationEnablePrompt(locationManager: LocationManager) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return

    DisposableEffect(Unit) {
        locationManager.checkLocationSettings(
            activity,
            onSuccess = {
                // Location settings are satisfied, proceed with your app's location functionality
            },
            onFailure = { exception ->
                try {
                    exception.startResolutionForResult(activity, REQUEST_CHECK_SETTINGS)
                } catch (sendEx: IntentSender.SendIntentException) {
                    // Ignore the error.
                }
            }
        )

        onDispose { }
    }
}

const val REQUEST_CHECK_SETTINGS = 1001