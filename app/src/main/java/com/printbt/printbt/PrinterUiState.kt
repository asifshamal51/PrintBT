package com.printbt.printbt

import android.bluetooth.BluetoothDevice
import android.net.Uri
// PrinterUiState.kt
data class PrinterUiState(
    val pairedDevices: List<BluetoothDevice> = emptyList(),
    val connectionStatus: String = "No printer connected",
    val isBluetoothEnabled: Boolean = false,
    val sharedImageUri: Uri? = null,
    val connectedDevice: BluetoothDevice? = null,
    val selectedPrintSize: PrintSize = PrintSize.RECEIPT_80MM,
    val isPrinting: Boolean = false,
    val showSnackbar: Boolean = false, // New property for Snackbar visibility
    val snackbarMessage: String = "", // New property for Snackbar message
    val isConnectionSuccess: Boolean = false // New property to determine Snackbar color
)

enum class PrintSize(val widthPx: Int, val label: String) {
    A4(794, "A4"),
    RECEIPT_55MM(384, "55mm Receipt"),
    RECEIPT_80MM(576, "80mm Receipt")
}