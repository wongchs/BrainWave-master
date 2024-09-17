package com.example.brainwave

import android.util.Log
import brainflow.BoardIds
import brainflow.BoardShim
import brainflow.BrainFlowError
import brainflow.BrainFlowInputParams


class BrainFlowManager {
    private val tag: String = "BrainFlowManager"
    private var boardShim: BoardShim? = null
    private var exits: Boolean = false
    private var macAddress: String = ""
    private var serialNumber: String = ""

    fun initBrainFlow(name: String, address: String) {
        macAddress = address
        serialNumber = name.filter { it.isDigit() || it == '.' }
        val boardIds = BoardIds.GANGLION_BOARD
        val params = BrainFlowInputParams()
        params.mac_address = macAddress
        params.ip_port = 0
        //params.serial_number = serialNumber
        //params.serial_port = "COM 3"
        Log.d(tag, "Try to connect device... \nBoard IDs: $boardIds\nDevice: $name\nMAC: $address\nserialNumber: $serialNumber")
        try {
            BoardShim.enable_dev_board_logger()
            boardShim = BoardShim(boardIds, params)
            boardShim?.prepare_session()
            startBrainFlow()
        } catch (e: BrainFlowError) {
            Log.w(tag,e.message.toString())
        }
    }

    private fun startBrainFlow() {
        try {
            boardShim?.start_stream()
            Thread {
                while (!exits) {
                    val data = getData()
                    val sb = StringBuilder()
                    if (data != null) {
                        for (row in data) {
                            for (value in row) {
                                sb.append(value).append("\t")
                            }
                            sb.append("\n")
                        }
                    }
                    Log.d(tag, sb.toString())
                    Thread.sleep(1000) // Sleep for 1 second before fetching data again
                }
            }.start()
        } catch (e: BrainFlowError) {
            Log.w(tag,e.message.toString())
        }
    }

    fun stopBrainFlow() {
        try {
            exits = true
            boardShim?.stop_stream()
            boardShim?.release_session()
        } catch (e: BrainFlowError) {
            e.printStackTrace()
        }
    }

    private fun getData(): Array<out DoubleArray>? {
        try {
            return boardShim?._board_data
        } catch (e: BrainFlowError) {
            e.printStackTrace()
        }
        return Array(0) { DoubleArray(0) }
    }


}