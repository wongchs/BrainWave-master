package com.example.brainwave

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.InputStream
import java.util.*

class BluetoothManager {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    fun connect(deviceAddress: String) {
        val device: BluetoothDevice = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            ?: throw Exception("Device not found")

        val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")  // Standard SerialPortService ID
        socket = device.createRfcommSocketToServiceRecord(uuid)
        socket?.connect()
        inputStream = socket?.inputStream
    }

    fun readData(): String {
        val buffer = ByteArray(1024)
        val bytes = inputStream?.read(buffer) ?: -1
        return String(buffer, 0, bytes)
    }

    fun close() {
        inputStream?.close()
        socket?.close()
    }
}
