package com.example.brainwave

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class BleDevice(val name: String, val address: String)

@Composable
fun BleDeviceList(devices: List<BleDevice>) {
    LazyColumn {
        items(devices) { device ->
            DeviceCard(name = device.name, address = device.address)
        }
    }
}

@Composable
fun DeviceCard(name: String, address: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 10.dp)
            .clickable {
                brainFlowManager.initBrainFlow(name, address)
            }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
        ) {
            Text(text = name)
            Text(text = address)
        }
    }
}

class BleScanCallback(
    private val onScanResultAction: (ScanResult?) -> Unit = {},
    private val onBatchScanResultAction: (MutableList<ScanResult>?) -> Unit = {},
    private val onScanFailedAction: (Int) -> Unit = {}
) : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult?) {
        super.onScanResult(callbackType, result)
        onScanResultAction(result)
    }

    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
        super.onBatchScanResults(results)
        onBatchScanResultAction(results)
    }

    override fun onScanFailed(errorCode: Int) {
        super.onScanFailed(errorCode)
        onScanFailedAction(errorCode)
    }
}

class BleScanManager(
    btManager: BluetoothManager,
    private val scanPeriod: Long = DEFAULT_SCAN_PERIOD,
    private val scanCallback: BleScanCallback = BleScanCallback()
) {
    private val btAdapter = btManager.adapter
    private val bleScanner = btAdapter.bluetoothLeScanner

    var beforeScanActions: MutableList<() -> Unit> = mutableListOf()
    var afterScanActions: MutableList<() -> Unit> = mutableListOf()

    private var scanning = false

    private val handler = Handler(Looper.getMainLooper())

    @SuppressLint("MissingPermission")
    fun scanBleDevices() {
        fun stopScan() {
            scanning = false    
            bleScanner.stopScan(scanCallback)

            // execute all the functions to execute after scanning
            executeAfterScanActions()
        }

        // scans for bluetooth LE devices
        if (scanning) {
            stopScan()
        } else {
            // stops scanning after scanPeriod millis
            handler.postDelayed({ stopScan() }, scanPeriod)
            // execute all the functions to execute before scanning
            executeBeforeScanActions()

            // starts scanning
            scanning = true
            bleScanner.startScan(scanCallback)
        }
    }

    companion object {
        const val DEFAULT_SCAN_PERIOD: Long = 10000

        private fun executeListOfFunctions(toExecute: List<() -> Unit>) {
            toExecute.forEach {
                it()
            }
        }
    }

    private fun executeBeforeScanActions() {
        executeListOfFunctions(beforeScanActions)
    }

    private fun executeAfterScanActions() {
        executeListOfFunctions(afterScanActions)
    }
}