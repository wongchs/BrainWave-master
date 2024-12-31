package com.example.brainwave.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

class LocationManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val geocoder: Geocoder = Geocoder(context, Locale.getDefault())
    private val settingsClient: SettingsClient = LocationServices.getSettingsClient(context)

    private val locationRequest = LocationRequest.create().apply {
        interval = 10000
        fastestInterval = 5000
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private var locationCallback: LocationCallback? = null

    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }

    fun checkLocationSettings(
        activity: Activity,
        onSuccess: () -> Unit,
        onFailure: (ResolvableApiException) -> Unit
    ) {
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

        settingsClient.checkLocationSettings(builder.build())
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    onFailure(exception)
                }
            }
    }

    fun getLastKnownLocation(
        activity: Activity,
        onPermissionDenied: () -> Unit,
        callback: (LocationData?) -> Unit
    ) {
        when {
            hasLocationPermissions() -> {
                try {
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { location: Location? ->
                            if (location != null) {
                                getAddressFromLocation(location) { address ->
                                    callback(LocationData(location, address))
                                }
                            } else {
                                callback(null)
                            }
                        }
                        .addOnFailureListener { e ->
                            callback(null)
                        }
                } catch (e: SecurityException) {
                    callback(null)
                }
            }

            shouldShowRequestPermissionRationale(activity) -> {
                // Show an explanation to the user
                onPermissionDenied()
            }

            else -> {
                requestLocationPermissions(activity)
            }
        }
    }

    fun startLocationUpdates(callback: (LocationData) -> Unit) {
        if (hasLocationPermissions()) {
            try {
                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        for (location in locationResult.locations) {
                            getAddressFromLocation(location) { address ->
                                callback(LocationData(location, address))
                            }
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback as LocationCallback,
                    Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                Log.e("LocationManager", "Security exception while requesting location updates", e)
            }
        } else {
            Log.e("LocationManager", "Location permissions not granted")
        }
    }


    fun startLocationUpdates(
        activity: Activity,
        onPermissionDenied: () -> Unit,
        callback: (LocationData) -> Unit
    ) {
        when {
            hasLocationPermissions() -> {
                try {
                    locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            for (location in locationResult.locations) {
                                getAddressFromLocation(location) { address ->
                                    callback(LocationData(location, address))
                                }
                            }
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback as LocationCallback,
                        Looper.getMainLooper()
                    )
                } catch (e: SecurityException) {
                    onPermissionDenied()
                }
            }

            shouldShowRequestPermissionRationale(activity) -> {
                onPermissionDenied()
            }

            else -> {
                requestLocationPermissions(activity)
            }
        }
    }

    fun stopLocationUpdates() {
        try {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        } catch (e: SecurityException) {
            // Handle the security exception if permissions were revoked
        }
    }

    private fun hasLocationPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun shouldShowRequestPermissionRationale(activity: Activity): Boolean {
        return REQUIRED_PERMISSIONS.any {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
        }
    }

    private fun requestLocationPermissions(activity: Activity) {
        ActivityCompat.requestPermissions(
            activity,
            REQUIRED_PERMISSIONS,
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun getAddressFromLocation(location: Location, callback: (String) -> Unit) {
        Thread {
            try {
                val addresses: List<Address>? =
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                if (addresses != null && addresses.isNotEmpty()) {
                    val address = addresses[0]
                    val addressParts = mutableListOf<String>()
                    for (i in 0..address.maxAddressLineIndex) {
                        addressParts.add(address.getAddressLine(i))
                    }
                    callback(addressParts.joinToString(", "))
                } else {
                    callback("Address not found")
                }
            } catch (e: IOException) {
                e.printStackTrace()
                callback("Error getting address")
            }
        }.start()
    }

    data class LocationData(
        val location: Location,
        val address: String
    )
}