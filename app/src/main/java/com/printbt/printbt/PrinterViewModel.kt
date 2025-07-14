package com.printbt.printbt

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log // Import Log class
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.data.printable.ImagePrintable
import com.mazenrashed.printooth.data.printable.Printable
import com.mazenrashed.printooth.ui.ScanningActivity
import com.mazenrashed.printooth.utilities.Printing
import com.mazenrashed.printooth.utilities.PrintingCallback
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PrinterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState

    private var printing: Printing? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var lastConnectedDevice: BluetoothDevice? = null
    private var appContext: Context? = null

    private val TAG = "PrinterViewModel" // Tag for Logcat

    @SuppressLint("MissingPermission")
    fun setContext(context: Context) {
        appContext = context.applicationContext
        Printooth.init(context)
        sharedPreferences = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
        loadPrintSize()
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
                lastConnectedDevice = device
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Connected to ${pairedPrinter?.name ?: "Unknown Device"}",
                    connectedDevice = device
                )
                Log.i(TAG, "Initialized with paired printer: ${pairedPrinter?.name ?: "Unknown Device"}")
            } else {
                Log.i(TAG, "No paired printer found on initialization")
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error during initialization: ${e.message}")
        }
    }

    private fun loadPrintSize() {
        val savedSize = sharedPreferences.getString("print_size", PrintSize.RECEIPT_80MM.name)
        val printSize = PrintSize.valueOf(savedSize ?: PrintSize.RECEIPT_80MM.name)
        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
    }

    fun setPrintSize(printSize: PrintSize) {
        sharedPreferences.edit { putString("print_size", printSize.name) }
        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
    }

    // New function to update text input
    fun updateTextToPrint(text: String) {
        _uiState.value = _uiState.value.copy(textToPrint = text)
        Log.d(TAG, "Text to print updated: $text")
    }

    // PrinterViewModel.kt
    fun printContent(context: Context) {
        viewModelScope.launch {
            if (printing == null) {
                lastConnectedDevice?.let { device ->
                    appContext?.let { ctx ->
                        connectToPrinter(ctx, device)
                        delay(1000)
                        if (printing == null) {
                            updateConnectionStatus("No printer connected. Please connect a printer.")
                            return@launch
                        }
                    } ?: run {
                        updateConnectionStatus("No context available for reconnection")
                        return@launch
                    }
                } ?: run {
                    updateConnectionStatus("No printer connected. Please connect a printer.")
                    return@launch
                }
            }

            val printables = ArrayList<Printable>()

            // Add text to printables if present
            if (_uiState.value.textToPrint.isNotBlank()) {
                try {
                    printables.add(
                        com.mazenrashed.printooth.data.printable.TextPrintable.Builder()
                            .setText(_uiState.value.textToPrint)
                            .setAlignment(0) // 0 for left, 1 for center, 2 for right
                            .setFontSize(1)  // 1 for normal, 0 for small, 2 for large
                            .build()
                    )
                    printables.add(
                        com.mazenrashed.printooth.data.printable.RawPrintable.Builder("\n\n\n\n".toByteArray()).build() // Updated to four newlines
                    )
                    Log.i(TAG, "Added text to printables: ${_uiState.value.textToPrint}")
                } catch (e: Exception) {
                    updateConnectionStatus("Error preparing text: ${e.message}")
                    Log.e(TAG, "Error preparing text: ${e.message}")
                    return@launch
                }
            }

            // Add image to printables if present
            _uiState.value.sharedImageUri?.let { uri ->
                try {
                    _uiState.value = _uiState.value.copy(isPrinting = true)
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    val resizedBitmap = resizeBitmap(originalBitmap, _uiState.value.selectedPrintSize.widthPx)
                    printables.add(ImagePrintable.Builder(resizedBitmap).build())
                    printables.add(com.mazenrashed.printooth.data.printable.RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                    Log.i(TAG, "Added image to printables")
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    updateConnectionStatus("Error printing image: ${e.message}")
                    Log.e(TAG, "Error printing image: ${e.message}")
                    return@launch
                }
            }

            if (printables.isEmpty()) {
                updateConnectionStatus("Nothing to print")
                Log.w(TAG, "No content (text or image) to print")
                return@launch
            }

            try {
                printing?.print(printables)
                Log.i(TAG, "Sent print job with ${printables.size} items")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isPrinting = false)
                updateConnectionStatus("Error printing: ${e.message}")
                Log.e(TAG, "Error printing: ${e.message}")
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    @SuppressLint("MissingPermission")
    fun connectToPrinter(context: Context, device: BluetoothDevice) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            Log.e(TAG, "Bluetooth permission denied when connecting to ${device.name}")
            return
        }
        try {
            if (printing != null && _uiState.value.connectedDevice == device) {
                updateConnectionStatus("Already connected to ${device.name}")
                Log.i(TAG, "Already connected to printer: ${device.name}")
                return
            }
            Printooth.setPrinter(device.name, device.address)
            printing = Printooth.printer()
            setupPrintingCallback()
            lastConnectedDevice = device
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Connected to ${device.name}",
                connectedDevice = device,
                showSnackbar = true,
                snackbarMessage = "Connected to ${device.name}",
                isConnectionSuccess = true
            )
            Log.i(TAG, "Successfully connected to printer: ${device.name}, address: ${device.address}")
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error when connecting to ${device.name}: ${e.message}")
        }
    }

    fun disconnectPrinter() {
        printing?.let {
            Printooth.removeCurrentPrinter()
            printing = null
            lastConnectedDevice = null
            _uiState.value = _uiState.value.copy(
                connectionStatus = "Printer disconnected",
                connectedDevice = null,
                isPrinting = false,
                showSnackbar = true,
                snackbarMessage = "Printer disconnected",
                isConnectionSuccess = false
            )
            Log.i(TAG, "Printer disconnected successfully")
        } ?: run {
            updateConnectionStatus("No printer connected")
            Log.w(TAG, "Disconnect attempted but no printer was connected")
        }
    }

    private fun setupPrintingCallback() {
        printing?.printingCallback = object : PrintingCallback {
            override fun connectingWithPrinter() {
                updateConnectionStatus("Connecting to printer...")
                Log.d(TAG, "Connecting to printer...")
            }

            override fun connectionFailed(error: String) {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Connection failed: $error",
                    connectedDevice = null,
                    isPrinting = false,
                    showSnackbar = true,
                    snackbarMessage = "Connection failed: $error",
                    isConnectionSuccess = false
                )
                printing = null
                Log.e(TAG, "Printer connection failed: $error")
            }

            override fun disconnected() {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Printer disconnected",
                    isPrinting = false,
                    showSnackbar = true,
                    snackbarMessage = "Printer disconnected",
                    isConnectionSuccess = false
                )
                Log.i(TAG, "Printer disconnected")
                // Attempt to reconnect
                lastConnectedDevice?.let { device ->
                    appContext?.let { ctx ->
                        viewModelScope.launch {
                            Log.d(TAG, "Attempting to reconnect to ${device.name}")
                            connectToPrinter(ctx, device)
                        }
                    } ?: run {
                        _uiState.value = _uiState.value.copy(connectedDevice = null)
                        printing = null
                        Log.w(TAG, "No context available for reconnection")
                    }
                } ?: run {
                    _uiState.value = _uiState.value.copy(connectedDevice = null)
                    printing = null
                    Log.w(TAG, "No last connected device for reconnection")
                }
            }

            override fun onError(error: String) {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Error: $error",
                    isPrinting = false,
                    showSnackbar = true,
                    snackbarMessage = "Error: $error",
                    isConnectionSuccess = false
                )
                Log.e(TAG, "Printer error: $error")
            }

            override fun onMessage(message: String) {
                updateConnectionStatus(message)
                Log.d(TAG, "Printer message: $message")
            }

            override fun printingOrderSentSuccessfully() {
                _uiState.value = _uiState.value.copy(
                    connectionStatus = "Print successful",
                    isPrinting = false,
                    showSnackbar = true,
                    snackbarMessage = "Print successful",
                    isConnectionSuccess = true
                )
                Log.i(TAG, "Print order sent successfully")
            }
        }
    }

    fun checkPermissionsAndBluetooth(context: Context) {
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
            if (context is MainActivity) {
                context.requestBluetoothPermission.launch(permissions)
                Log.d(TAG, "Requesting Bluetooth permissions")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkBluetoothAndLoadDevices(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            Log.e(TAG, "Bluetooth permission denied when checking Bluetooth")
            return
        }
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                updateConnectionStatus("No Bluetooth adapter available")
                Log.e(TAG, "No Bluetooth adapter available")
                return
            }
            val isEnabled = bluetoothAdapter.isEnabled
            _uiState.value = _uiState.value.copy(isBluetoothEnabled = isEnabled)
            if (isEnabled) {
                loadPairedDevices(context)
                Log.d(TAG, "Bluetooth is enabled, loading paired devices")
            } else {
                updateConnectionStatus("Bluetooth is off")
                Log.w(TAG, "Bluetooth is disabled")
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            Log.e(TAG, "Bluetooth permission denied when loading paired devices")
            return
        }
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val devices = bluetoothAdapter.bondedDevices.toList()
            _uiState.value = _uiState.value.copy(pairedDevices = devices)
            if (devices.isEmpty()) {
                updateConnectionStatus("No paired printers found")
                Log.w(TAG, "No paired printers found")
            } else {
                Log.i(TAG, "Found ${devices.size} paired devices: ${devices.joinToString { it.name ?: "Unknown" }}")
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error when loading devices: ${e.message}")
        }
    }

    fun enableBluetooth(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            updateConnectionStatus("Bluetooth permission denied")
            Log.e(TAG, "Bluetooth permission denied when enabling Bluetooth")
            return
        }
        try {
            context.enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            Log.d(TAG, "Launching Bluetooth enable intent")
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error: ${e.message}")
        }
    }

    fun scanForPrinters(context: MainActivity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            updateConnectionStatus("Bluetooth scan or location permission denied")
            Log.e(TAG, "Bluetooth scan or location permission denied")
            return
        }
        try {
            context.scanPrinter.launch(Intent(context, ScanningActivity::class.java))
            Log.d(TAG, "Launching printer scan activity")
        } catch (e: SecurityException) {
            updateConnectionStatus("Permission error: ${e.message}")
            Log.e(TAG, "Permission error when scanning for printers: ${e.message}")
        }
    }

    fun onBluetoothEnabled(context: MainActivity) {
        _uiState.value = _uiState.value.copy(isBluetoothEnabled = true)
        loadPairedDevices(context)
        Log.i(TAG, "Bluetooth enabled successfully")
    }

    fun onPrinterScanned(context: MainActivity) {
        updateConnectionStatus("Printer paired successfully")
        loadPairedDevices(context)
        Log.i(TAG, "Printer paired successfully")
    }

    // PrinterViewModel.kt
    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                // Handle image URI
                val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    _uiState.value = _uiState.value.copy(sharedImageUri = uri)
                    Log.i(TAG, "Received shared image URI: $uri")
                }
                // Handle text
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    _uiState.value = _uiState.value.copy(textToPrint = text)
                    Log.i(TAG, "Received shared text: $text")
                }
            }
        }
    }

//    fun handleIntent(intent: Intent?) {
//        if (intent?.action == Intent.ACTION_SEND) {
//            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
//            if (uri != null) {
//                _uiState.value = _uiState.value.copy(sharedImageUri = uri)
//                Log.i(TAG, "Received shared image URI: $uri")
//            } else {
//                Log.w(TAG, "Received ACTION_SEND intent but no URI found")
//            }
//        }
//    }

    fun refreshDevices(context: Context) {
        loadPairedDevices(context)
        Log.d(TAG, "Refreshing paired devices")
    }

    internal fun updateConnectionStatus(status: String) {
        val isSuccess = status.contains("Connected to") || status == "Print successful"
        _uiState.value = _uiState.value.copy(
            connectionStatus = status,
            showSnackbar = true,
            snackbarMessage = status,
            isConnectionSuccess = isSuccess
        )
        Log.d(TAG, "Connection status updated: $status")
    }

    fun dismissSnackbar() {
        _uiState.value = _uiState.value.copy(showSnackbar = false)
        Log.d(TAG, "Snackbar dismissed")
    }
}