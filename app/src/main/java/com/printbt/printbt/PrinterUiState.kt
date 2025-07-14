package com.printbt.printbt

import android.bluetooth.BluetoothDevice
import android.net.Uri

data class PrinterUiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val connectionStatus: String = "No printer connected",
    val isBluetoothEnabled: Boolean = false,
    val sharedImageUri: Uri? = null,
    val connectedDevice: BluetoothDevice? = null // Add this
)