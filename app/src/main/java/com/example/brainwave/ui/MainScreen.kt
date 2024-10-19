package com.example.brainwave.ui

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.provider.Settings
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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

@Composable
fun HomeScreen(
    context: Context,
    receivedData: String,
    seizureData: Triple<String, List<Float>, LocationManager.LocationData?>?
) {
    BluetoothEnablePrompt(context)
    val locationManager = remember { LocationManager(context) }
    LocationEnablePrompt(locationManager)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        BluetoothReceiver(context, receivedData)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Quick Actions",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }
}

@Composable
fun BluetoothEnablePrompt(context: Context) {
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    var showPrompt by remember { mutableStateOf(!bluetoothAdapter?.isEnabled!! ?: false) }

    if (showPrompt) {
        AlertDialog(
            onDismissRequest = { showPrompt = false },
            title = { Text("Enable Bluetooth") },
            text = { Text("Bluetooth is required for this app to function properly. Would you like to enable it?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPrompt = false
                        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                        try {
                            (context as? Activity)?.startActivityForResult(enableBtIntent, 1)
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(
                                context,
                                "Unable to enable Bluetooth. Please enable it manually.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                ) {
                    Text("Enable")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrompt = false }) {
                    Text("Cancel")
                }
            }
        )
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