package com.printbt.printbt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.data.printable.ImagePrintable
import com.mazenrashed.printooth.data.printable.Printable
import com.mazenrashed.printooth.ui.ScanningActivity
import com.mazenrashed.printooth.utilities.Printing
import com.mazenrashed.printooth.utilities.PrintingCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PrinterViewModel : ViewModel() {


    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState

    private var printing: Printing? = null

    // PrinterViewModel.kt
    @SuppressLint("MissingPermission")
    fun setContext(context: MainActivity) {
        Printooth.init(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            return
        }
        try {
            if (Printooth.hasPairedPrinter()) {
                printing = Printooth.printer()
                setupPrintingCallback()
                val pairedPrinter = Printooth.getPairedPrinter()
                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
                val device = pairedPrinter?.address?.let { address ->
                    bluetoothAdapter.getRemoteDevice(address)
                }
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Connected to ${pairedPrinter?.name ?: "Unknown Device"}",
                    connectedDevice = device
                )
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
        }
    }

    private fun setupPrintingCallback() {
        printing?.printingCallback = object : PrintingCallback {
            override fun connectingWithPrinter() {
                updateConnectionStatus("Connecting to printer...")
            }

            override fun connectionFailed(error: String) {
                updateConnectionStatus("Connection failed: $error")
                _uiState.value = _uiState.value.copy(connectedDevice = null)
            }

            override fun disconnected() {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Printer disconnected",
                    connectedDevice = null
                )
                printing = null // Clear the printing instance
            }

            override fun onError(error: String) {
                updateConnectionStatus("Error: $error")
            }

            override fun onMessage(message: String) {
                updateConnectionStatus(message)
            }

            override fun printingOrderSentSuccessfully() {
                updateConnectionStatus("Print successful")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToPrinter(context: MainActivity, device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            return
        }

        try {
            if (printing != null && _uiState.value.connectedDevice == device) {
                updateConnectionStatus("Already connected to ${device.name}")
                return
            }
            Printooth.setPrinter(device.name, device.address)
            printing = Printooth.printer()
            setupPrintingCallback()
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Connected to ${device.name}",
                connectedDevice = device
            )
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
        }
    }

    fun disconnectPrinter() {
        printing?.let {
            Printooth.removeCurrentPrinter()
            printing = null
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Printer disconnected",
                connectedDevice = null
            )
        } ?: run {
            updateConnectionStatus("No printer connected")
        }
    }

    fun printImage(context: MainActivity) {
        viewModelScope.launch {
            if (printing == null) {
                updateConnectionStatus("No printer connected. Please connect a printer.")
                return@launch
            }
            _uiState.value.sharedImageUri?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    val printables = ArrayList<Printable>().apply {
                        add(ImagePrintable.Builder(bitmap).build())
                    }
                    printing?.print(printables) // Reuse existing printing instance
                } catch (e: Exception) {
                    updateConnectionStatus("Error printing image: ${e.message}")
                }
            } ?: run {
                updateConnectionStatus("No image to print")
            }
        }
    }

    fun checkPermissionsAndBluetooth(context: MainActivity) {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }) {
            checkBluetoothAndLoadDevices(context)
        } else {
            context.requestBluetoothPermission.launch(permissions)
        }
    }

    @SuppressLint("MissingPermission")
    fun checkBluetoothAndLoadDevices(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            return
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                updateConnectionStatus("No Bluetooth adapter available")
                return
            }
            val isEnabled = bluetoothAdapter.isEnabled
            _uiState.value = _uiState.value.copy(isBluetoothEnabled = isEnabled)
            if (isEnabled) {
                loadPairedDevices(context)
            } else {
                updateConnectionStatus("Bluetooth is off")
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            return
        }

        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val devices = bluetoothAdapter.bondedDevices.toList()
            _uiState.value = _uiState.value.copy(pairedDevices = devices)
            if (devices.isEmpty()) {
                updateConnectionStatus("No paired printers found")
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
        }
    }


    fun enableBluetooth(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            return
        }

        try {
            context.enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
        }
    }

    fun scanForPrinters(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            updateConnectionStatus("Bluetooth scan or location permission denied")
            return
        }

        try {
            context.scanPrinter.launch(Intent(context, ScanningActivity::class.java))
        } catch (e: SecurityException) {
            updateConnectionStatus("Permission error: ${e.message}")
        }
    }

    fun onBluetoothEnabled(context: MainActivity) {
        _uiState.value = _uiState.value.copy(isBluetoothEnabled = true)
        loadPairedDevices(context)
    }

    fun onPrinterScanned(context: MainActivity) {
        updateConnectionStatus("Printer paired successfully")
        loadPairedDevices(context)
    }


    // Update handleIntent to accept sharing from any app
    fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                _uiState.value = _uiState.value.copy(sharedImageUri = uri)
            }
        }
    }


    fun refreshDevices(context: MainActivity) {
        loadPairedDevices(context)
    }

    internal fun updateConnectionStatus(status: String) {
        _uiState.value = _uiState.value.copy(connectionStatus = status)
    }
}