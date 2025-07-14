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
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.data.printable.ImagePrintable
import com.mazenrashed.printooth.data.printable.Printable
import com.mazenrashed.printooth.data.printable.RawPrintable
import com.mazenrashed.printooth.data.printable.TextPrintable
import com.mazenrashed.printooth.ui.ScanningActivity
import com.mazenrashed.printooth.utilities.Printing
import com.mazenrashed.printooth.utilities.PrintingCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.IOException

class PrinterViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PrinterUiState())
    val uiState: StateFlow<PrinterUiState> = _uiState

    private var printing: Printing? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var lastConnectedDevice: BluetoothDevice? = null
    private var appContext: Context? = null
    private var printBitmaps: List<Bitmap> = emptyList()

    private val TAG = "PrinterViewModel"

    @SuppressLint("MissingPermission")
    fun setContext(context: Context) {
        appContext = context.applicationContext
        Printooth.init(context)
        sharedPreferences = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
        loadPrintSize()
        Log.d(TAG, "Context set, checking permissions")
        checkPermissionsAndBluetooth(context)
    }

    private fun loadPrintSize() {
        val savedSize = sharedPreferences.getString("print_size", PrintSize.RECEIPT_80MM.name)
        val printSize = PrintSize.valueOf(savedSize ?: PrintSize.RECEIPT_80MM.name)
        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
        Log.d(TAG, "Loaded print size: $printSize")
    }

    fun setPrintSize(printSize: PrintSize) {
        sharedPreferences.edit { putString("print_size", printSize.name) }
        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
        Log.d(TAG, "Print size set to: $printSize")
    }

    fun updateTextToPrint(text: String) {
        _uiState.value = _uiState.value.copy(textToPrint = text)
        Log.d(TAG, "Text to print updated: $text")
    }

    fun updateWebpageUrl(url: String) {
        _uiState.value = _uiState.value.copy(webpageUrl = url)
        Log.d(TAG, "Webpage URL updated: $url")
    }

    fun updatePrintBitmaps(bitmaps: List<Bitmap>) {
        printBitmaps = bitmaps
        _uiState.value = _uiState.value.copy(printBitmaps = bitmaps)
        Log.d(TAG, "Updated print bitmaps: ${bitmaps.size} pages")
    }

    fun printWebpageAsBitmap(context: Context, url: String, callback: (Bitmap?) -> Unit) {
        val webView = WebView(context)
        webView.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Mobile Safari/537.36"
        }

        val receiptWidth = _uiState.value.selectedPrintSize.widthPx
        val defaultHeight = 3000
        webView.layout(0, 0, receiptWidth, defaultHeight)

        webView.loadUrl("javascript:(function() { var meta = document.querySelector('meta[name=viewport]'); if (meta) { meta.setAttribute('content', 'width=$receiptWidth, initial-scale=1.0'); } else { var newMeta = document.createElement('meta'); newMeta.name = 'viewport'; newMeta.content = 'width=$receiptWidth, initial-scale=1.0'; document.head.appendChild(newMeta); } })()")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "WebView onPageFinished: $url, width=${view?.width}, contentHeight=${view?.contentHeight}")
                view?.postDelayed({
                    val contentHeight = view.contentHeight.takeIf { it > 0 } ?: defaultHeight
                    if (contentHeight <= 0 || view.width <= 0) {
                        Log.e(TAG, "Invalid WebView dimensions: width=${view.width}, height=$contentHeight")
                        callback(null)
                        return@postDelayed
                    }
                    try {
                        val bitmap = Bitmap.createBitmap(view.width, contentHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)
                        view.draw(canvas)
                        Log.i(TAG, "Webpage bitmap created: width=${view.width}, height=$contentHeight")
                        callback(bitmap)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error creating bitmap: ${e.message}")
                        callback(null)
                    }
                }, 3000)
            }

            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                Log.e(TAG, "WebView error: $description, code=$errorCode, url=$failingUrl")
                callback(null)
            }
        }

        if (url.isBlank() || !url.matches(Regex("https?://.*"))) {
            Log.e(TAG, "Invalid URL: $url")
            callback(null)
            return
        }

        viewModelScope.launch {
            delay(15000)
            if (_uiState.value.isPrinting) {
                Log.e(TAG, "Webpage rendering timed out for URL: $url")
                callback(null)
            }
        }

        webView.loadUrl(url)
        Log.d(TAG, "Loading webpage: $url")
    }

    private suspend fun fetchWebpageText(url: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val document = Jsoup.connect(url).get()
                val content = document.select("body").text().take(1000)
                content
            } catch (e: IOException) {
                Log.e(TAG, "Error fetching webpage text: ${e.message}")
                "Error fetching webpage: ${e.message}"
            }
        }
    }

    fun printContent(context: Context) {
        viewModelScope.launch {
            Log.d(TAG, "Starting printContent, isPrinting: ${_uiState.value.isPrinting}")
            if (printing == null) {
                lastConnectedDevice?.let { device ->
                    appContext?.let { ctx ->
                        Log.d(TAG, "No printer connected, attempting to reconnect to ${device.name}")
                        connectToPrinter(ctx, device)
                        delay(1000)
                        if (printing == null) {
                            updateConnectionStatus("No printer connected. Please connect a printer.")
                            Log.w(TAG, "Reconnection failed, printing aborted")
                            _uiState.value = _uiState.value.copy(isPrinting = false)
                            return@launch
                        }
                    } ?: run {
                        updateConnectionStatus("No context available for reconnection")
                        Log.w(TAG, "No context available, printing aborted")
                        _uiState.value = _uiState.value.copy(isPrinting = false)
                        return@launch
                    }
                } ?: run {
                    updateConnectionStatus("No printer connected. Please connect a printer.")
                    Log.w(TAG, "No last connected device, printing aborted")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    return@launch
                }
            }

            val printables = ArrayList<Printable>()
            _uiState.value = _uiState.value.copy(isPrinting = true)
            Log.d(TAG, "Set isPrinting to true")

            if (printBitmaps.isNotEmpty()) {
                try {
                    printBitmaps.forEach { bitmap ->
                        val resizedBitmap = resizeBitmap(bitmap, _uiState.value.selectedPrintSize.widthPx)
                        printables.add(ImagePrintable.Builder(resizedBitmap).build())
                        printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                        Log.i(TAG, "Added print job bitmap to printables: ${resizedBitmap.width}x${resizedBitmap.height}")
                    }
                } catch (e: Exception) {
                    updateConnectionStatus("Error preparing print job bitmap: ${e.message}")
                    Log.e(TAG, "Error preparing print job bitmap: ${e.message}")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    return@launch
                }
            }

            if (_uiState.value.textToPrint.isNotBlank()) {
                try {
                    printables.add(
                        TextPrintable.Builder()
                            .setText(_uiState.value.textToPrint)
                            .setAlignment(0)
                            .setFontSize(1)
                            .build()
                    )
                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                    Log.i(TAG, "Added text to printables: ${_uiState.value.textToPrint}")
                } catch (e: Exception) {
                    updateConnectionStatus("Error preparing text: ${e.message}")
                    Log.e(TAG, "Error preparing text: ${e.message}")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    return@launch
                }
            }

            _uiState.value.sharedImageUri?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    if (originalBitmap == null) {
                        updateConnectionStatus("Failed to decode image")
                        Log.e(TAG, "Failed to decode image from URI: $uri")
                        _uiState.value = _uiState.value.copy(isPrinting = false)
                        return@launch
                    }
                    val resizedBitmap = resizeBitmap(originalBitmap, _uiState.value.selectedPrintSize.widthPx)
                    printables.add(ImagePrintable.Builder(resizedBitmap).build())
                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                    Log.i(TAG, "Added image to printables, dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
                } catch (e: Exception) {
                    updateConnectionStatus("Error printing image: ${e.message}")
                    Log.e(TAG, "Error printing image: ${e.message}")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    return@launch
                }
            }

            if (_uiState.value.webpageUrl.isNotBlank()) {
                try {
                    printWebpageAsBitmap(context, _uiState.value.webpageUrl) { bitmap ->
                        viewModelScope.launch {
                            if (bitmap != null) {
                                val resizedBitmap = resizeBitmap(bitmap, _uiState.value.selectedPrintSize.widthPx)
                                printables.add(ImagePrintable.Builder(resizedBitmap).build())
                                printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                                Log.i(TAG, "Added webpage bitmap to printables, dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
                            } else {
                                Log.w(TAG, "Webpage bitmap failed, falling back to text")
                                val webpageText = fetchWebpageText(_uiState.value.webpageUrl)
                                if (webpageText.startsWith("Error")) {
                                    updateConnectionStatus(webpageText)
                                    Log.e(TAG, "Failed to fetch webpage text")
                                } else {
                                    printables.add(
                                        TextPrintable.Builder()
                                            .setText(webpageText)
                                            .setAlignment(0)
                                            .setFontSize(1)
                                            .build()
                                    )
                                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
                                    Log.i(TAG, "Added webpage text to printables")
                                }
                            }

                            if (printables.isEmpty()) {
                                _uiState.value = _uiState.value.copy(isPrinting = false)
                                updateConnectionStatus("Nothing to print")
                                Log.w(TAG, "No content (text, image, or webpage) to print")
                                return@launch
                            }

                            try {
                                Log.d(TAG, "Sending print job with ${printables.size} items")
                                printing?.print(printables)
                            } catch (e: Exception) {
                                updateConnectionStatus("Error printing: ${e.message}")
                                Log.e(TAG, "Error printing: ${e.message}")
                                _uiState.value = _uiState.value.copy(isPrinting = false)
                            }
                        }
                    }
                } catch (e: Exception) {
                    updateConnectionStatus("Error processing webpage: ${e.message}")
                    Log.e(TAG, "Error processing webpage: ${e.message}")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    return@launch
                }
            } else {
                if (printables.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                    updateConnectionStatus("Nothing to print")
                    Log.w(TAG, "No content (text, image, or webpage) to print")
                    return@launch
                }
                try {
                    Log.d(TAG, "Sending print job with ${printables.size} items")
                    printing?.print(printables)
                } catch (e: Exception) {
                    updateConnectionStatus("Error printing: ${e.message}")
                    Log.e(TAG, "Error printing: ${e.message}")
                    _uiState.value = _uiState.value.copy(isPrinting = false)
                }
            }
        }
    }

    fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) {
                    _uiState.value = _uiState.value.copy(sharedImageUri = uri, webpageUrl = "", textToPrint = "")
                    Log.i(TAG, "Received shared image URI: $uri")
                }
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    if (text.startsWith("http://") || text.startsWith("https://")) {
                        _uiState.value = _uiState.value.copy(webpageUrl = text, sharedImageUri = null, textToPrint = "")
                        Log.i(TAG, "Received shared webpage URL: $text")
                    } else {
                        _uiState.value = _uiState.value.copy(textToPrint = text, sharedImageUri = null, webpageUrl = "")
                        Log.i(TAG, "Received shared text: $text")
                    }
                }
            }
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        val aspectRatio = bitmap.height.toFloat() / bitmap.width
        val targetHeight = (targetWidth * aspectRatio).toInt()
        Log.d(TAG, "Resizing bitmap: original=${bitmap.width}x${bitmap.height}, target=${targetWidth}x$targetHeight")
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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        Log.d(TAG, "Checking permissions: ${permissions.joinToString()}, missing: ${missingPermissions.joinToString()}")

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions granted")
            checkBluetoothAndLoadDevices(context)
        } else {
            if (context is MainActivity) {
                Log.d(TAG, "Requesting permissions: ${missingPermissions.joinToString()}")
                context.requestBluetoothPermission.launch(permissions)
            } else {
                updateConnectionStatus("Cannot request permissions: Invalid context")
                Log.e(TAG, "Cannot request permissions: Context is not MainActivity")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun checkBluetoothAndLoadDevices(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
                // Attempt to restore last connected printer
                if (Printooth.hasPairedPrinter()) {
                    val pairedPrinter = Printooth.getPairedPrinter()
                    val device = pairedPrinter?.address?.let { address ->
                        bluetoothAdapter.getRemoteDevice(address)
                    }
                    if (device != null) {
                        connectToPrinter(context, device)
                    }
                }
            }
        } catch (e: SecurityException) {
            updateConnectionStatus("Bluetooth permission error: ${e.message}")
            Log.e(TAG, "Bluetooth permission error when loading devices: ${e.message}")
        }
    }

    fun enableBluetooth(context: MainActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
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


//package com.printbt.printbt
//
//import android.Manifest
//import android.annotation.SuppressLint
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.content.Context
//import android.content.Intent
//import android.content.SharedPreferences
//import android.content.pm.PackageManager
//import android.graphics.Bitmap
//import android.graphics.BitmapFactory
//import android.graphics.Canvas
//import android.net.Uri
//import android.util.Log
//import android.webkit.WebView
//import android.webkit.WebViewClient
//import androidx.core.content.ContextCompat
//import androidx.core.content.edit
//import androidx.lifecycle.ViewModel
//import androidx.lifecycle.viewModelScope
//import com.mazenrashed.printooth.Printooth
//import com.mazenrashed.printooth.data.printable.ImagePrintable
//import com.mazenrashed.printooth.data.printable.Printable
//import com.mazenrashed.printooth.data.printable.RawPrintable
//import com.mazenrashed.printooth.data.printable.TextPrintable
//import com.mazenrashed.printooth.ui.ScanningActivity
//import com.mazenrashed.printooth.utilities.Printing
//import com.mazenrashed.printooth.utilities.PrintingCallback
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.flow.MutableStateFlow
//import kotlinx.coroutines.flow.StateFlow
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import org.jsoup.Jsoup
//import java.io.IOException
//
//class PrinterViewModel : ViewModel() {
//    private val _uiState = MutableStateFlow(PrinterUiState())
//    val uiState: StateFlow<PrinterUiState> = _uiState
//
//    private var printing: Printing? = null
//    private lateinit var sharedPreferences: SharedPreferences
//    private var lastConnectedDevice: BluetoothDevice? = null
//    private var appContext: Context? = null
//    private var printBitmaps: List<Bitmap> = emptyList()
//
//    private val TAG = "PrinterViewModel"
//
//    @SuppressLint("MissingPermission")
//    fun setContext(context: Context) {
//        appContext = context.applicationContext
//        Printooth.init(context)
//        sharedPreferences = context.getSharedPreferences("PrinterPrefs", Context.MODE_PRIVATE)
//        loadPrintSize()
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            updateConnectionStatus("Bluetooth permission denied")
//            return
//        }
//        try {
//            if (Printooth.hasPairedPrinter()) {
//                printing = Printooth.printer()
//                setupPrintingCallback()
//                val pairedPrinter = Printooth.getPairedPrinter()
//                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//                val device = pairedPrinter?.address?.let { address ->
//                    bluetoothAdapter.getRemoteDevice(address)
//                }
//                lastConnectedDevice = device
//                _uiState.value = _uiState.value.copy(
//                    connectionStatus = "Connected to ${pairedPrinter?.name ?: "Unknown Device"}",
//                    connectedDevice = device
//                )
//                Log.i(TAG, "Initialized with paired printer: ${pairedPrinter?.name ?: "Unknown Device"}")
//            } else {
//                Log.i(TAG, "No paired printer found on initialization")
//            }
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Bluetooth permission error: ${e.message}")
//            Log.e(TAG, "Bluetooth permission error during initialization: ${e.message}")
//        }
//    }
//
//    private fun loadPrintSize() {
//        val savedSize = sharedPreferences.getString("print_size", PrintSize.RECEIPT_80MM.name)
//        val printSize = PrintSize.valueOf(savedSize ?: PrintSize.RECEIPT_80MM.name)
//        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
//    }
//
//    fun setPrintSize(printSize: PrintSize) {
//        sharedPreferences.edit { putString("print_size", printSize.name) }
//        _uiState.value = _uiState.value.copy(selectedPrintSize = printSize)
//    }
//
//    fun updateTextToPrint(text: String) {
//        _uiState.value = _uiState.value.copy(textToPrint = text)
//        Log.d(TAG, "Text to print updated: $text")
//    }
//
//    fun updateWebpageUrl(url: String) {
//        _uiState.value = _uiState.value.copy(webpageUrl = url)
//        Log.d(TAG, "Webpage URL updated: $url")
//    }
//
//    fun updatePrintBitmaps(bitmaps: List<Bitmap>) {
//        printBitmaps = bitmaps
//        Log.d(TAG, "Updated print bitmaps: ${bitmaps.size} pages")
//    }
//
//    fun printWebpageAsBitmap(context: Context, url: String, callback: (Bitmap?) -> Unit) {
//        val webView = WebView(context)
//        webView.settings.apply {
//            javaScriptEnabled = true
//            useWideViewPort = true
//            loadWithOverviewMode = true
//            domStorageEnabled = true
//            databaseEnabled = true
//            userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Mobile Safari/537.36"
//        }
//
//        val receiptWidth = _uiState.value.selectedPrintSize.widthPx
//        val defaultHeight = 3000
//        webView.layout(0, 0, receiptWidth, defaultHeight)
//
//        webView.loadUrl("javascript:(function() { var meta = document.querySelector('meta[name=viewport]'); if (meta) { meta.setAttribute('content', 'width=$receiptWidth, initial-scale=1.0'); } else { var newMeta = document.createElement('meta'); newMeta.name = 'viewport'; newMeta.content = 'width=$receiptWidth, initial-scale=1.0'; document.head.appendChild(newMeta); } })()")
//
//        webView.webViewClient = object : WebViewClient() {
//            override fun onPageFinished(view: WebView?, url: String?) {
//                Log.d(TAG, "WebView onPageFinished: $url, width=${view?.width}, contentHeight=${view?.contentHeight}")
//                view?.postDelayed({
//                    val contentHeight = view.contentHeight.takeIf { it > 0 } ?: defaultHeight
//                    if (contentHeight <= 0 || view.width <= 0) {
//                        Log.e(TAG, "Invalid WebView dimensions: width=${view.width}, height=$contentHeight")
//                        callback(null)
//                        return@postDelayed
//                    }
//                    try {
//                        val bitmap = Bitmap.createBitmap(view.width, contentHeight, Bitmap.Config.ARGB_8888)
//                        val canvas = Canvas(bitmap)
//                        view.draw(canvas)
//                        Log.i(TAG, "Webpage bitmap created: width=${view.width}, height=$contentHeight")
//                        callback(bitmap)
//                    } catch (e: Exception) {
//                        Log.e(TAG, "Error creating bitmap: ${e.message}")
//                        callback(null)
//                    }
//                }, 3000)
//            }
//
//            override fun onReceivedError(
//                view: WebView?,
//                errorCode: Int,
//                description: String?,
//                failingUrl: String?
//            ) {
//                Log.e(TAG, "WebView error: $description, code=$errorCode, url=$failingUrl")
//                callback(null)
//            }
//        }
//
//        if (url.isBlank() || !url.matches(Regex("https?://.*"))) {
//            Log.e(TAG, "Invalid URL: $url")
//            callback(null)
//            return
//        }
//
//        viewModelScope.launch {
//            delay(15000)
//            if (_uiState.value.isPrinting) {
//                Log.e(TAG, "Webpage rendering timed out for URL: $url")
//                callback(null)
//            }
//        }
//
//        webView.loadUrl(url)
//        Log.d(TAG, "Loading webpage: $url")
//    }
//
//    private suspend fun fetchWebpageText(url: String): String {
//        return withContext(Dispatchers.IO) {
//            try {
//                val document = Jsoup.connect(url).get()
//                val content = document.select("body").text().take(1000)
//                content
//            } catch (e: IOException) {
//                Log.e(TAG, "Error fetching webpage text: ${e.message}")
//                "Error fetching webpage: ${e.message}"
//            }
//        }
//    }
//
//    fun printContent(context: Context) {
//        viewModelScope.launch {
//            Log.d(TAG, "Starting printContent, isPrinting: ${_uiState.value.isPrinting}")
//            if (printing == null) {
//                lastConnectedDevice?.let { device ->
//                    appContext?.let { ctx ->
//                        Log.d(TAG, "No printer connected, attempting to reconnect to ${device.name}")
//                        connectToPrinter(ctx, device)
//                        delay(1000)
//                        if (printing == null) {
//                            updateConnectionStatus("No printer connected. Please connect a printer.")
//                            Log.w(TAG, "Reconnection failed, printing aborted")
//                            _uiState.value = _uiState.value.copy(isPrinting = false)
//                            return@launch
//                        }
//                    } ?: run {
//                        updateConnectionStatus("No context available for reconnection")
//                        Log.w(TAG, "No context available, printing aborted")
//                        _uiState.value = _uiState.value.copy(isPrinting = false)
//                        return@launch
//                    }
//                } ?: run {
//                    updateConnectionStatus("No printer connected. Please connect a printer.")
//                    Log.w(TAG, "No last connected device, printing aborted")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    return@launch
//                }
//            }
//
//            val printables = ArrayList<Printable>()
//            _uiState.value = _uiState.value.copy(isPrinting = true)
//            Log.d(TAG, "Set isPrinting to true")
//
//            // Add print job bitmaps (from Chrome print)
//            if (printBitmaps.isNotEmpty()) {
//                try {
//                    printBitmaps.forEach { bitmap ->
//                        val resizedBitmap = resizeBitmap(bitmap, _uiState.value.selectedPrintSize.widthPx)
//                        printables.add(ImagePrintable.Builder(resizedBitmap).build())
//                        printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
//                        Log.i(TAG, "Added print job bitmap to printables: ${resizedBitmap.width}x${resizedBitmap.height}")
//                    }
//                } catch (e: Exception) {
//                    updateConnectionStatus("Error preparing print job bitmap: ${e.message}")
//                    Log.e(TAG, "Error preparing print job bitmap: ${e.message}")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    return@launch
//                }
//            }
//
//            // Add text to printables if present
//            if (_uiState.value.textToPrint.isNotBlank()) {
//                try {
//                    printables.add(
//                        TextPrintable.Builder()
//                            .setText(_uiState.value.textToPrint)
//                            .setAlignment(0)
//                            .setFontSize(1)
//                            .build()
//                    )
//                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
//                    Log.i(TAG, "Added text to printables: ${_uiState.value.textToPrint}")
//                } catch (e: Exception) {
//                    updateConnectionStatus("Error preparing text: ${e.message}")
//                    Log.e(TAG, "Error preparing text: ${e.message}")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    return@launch
//                }
//            }
//
//            // Add image to printables if present
//            _uiState.value.sharedImageUri?.let { uri ->
//                try {
//                    val inputStream = context.contentResolver.openInputStream(uri)
//                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
//                    inputStream?.close()
//                    if (originalBitmap == null) {
//                        updateConnectionStatus("Failed to decode image")
//                        Log.e(TAG, "Failed to decode image from URI: $uri")
//                        _uiState.value = _uiState.value.copy(isPrinting = false)
//                        return@launch
//                    }
//                    val resizedBitmap = resizeBitmap(originalBitmap, _uiState.value.selectedPrintSize.widthPx)
//                    printables.add(ImagePrintable.Builder(resizedBitmap).build())
//                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
//                    Log.i(TAG, "Added image to printables, dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
//                } catch (e: Exception) {
//                    updateConnectionStatus("Error printing image: ${e.message}")
//                    Log.e(TAG, "Error printing image: ${e.message}")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    return@launch
//                }
//            }
//
//            // Add webpage content (as bitmap or text fallback)
//            if (_uiState.value.webpageUrl.isNotBlank()) {
//                try {
//                    printWebpageAsBitmap(context, _uiState.value.webpageUrl) { bitmap ->
//                        viewModelScope.launch {
//                            if (bitmap != null) {
//                                val resizedBitmap = resizeBitmap(bitmap, _uiState.value.selectedPrintSize.widthPx)
//                                printables.add(ImagePrintable.Builder(resizedBitmap).build())
//                                printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
//                                Log.i(TAG, "Added webpage bitmap to printables, dimensions: ${resizedBitmap.width}x${resizedBitmap.height}")
//                            } else {
//                                Log.w(TAG, "Webpage bitmap failed, falling back to text")
//                                val webpageText = fetchWebpageText(_uiState.value.webpageUrl)
//                                if (webpageText.startsWith("Error")) {
//                                    updateConnectionStatus(webpageText)
//                                    Log.e(TAG, "Failed to fetch webpage text")
//                                } else {
//                                    printables.add(
//                                        TextPrintable.Builder()
//                                            .setText(webpageText)
//                                            .setAlignment(0)
//                                            .setFontSize(1)
//                                            .build()
//                                    )
//                                    printables.add(RawPrintable.Builder("\n\n\n\n".toByteArray()).build())
//                                    Log.i(TAG, "Added webpage text to printables")
//                                }
//                            }
//
//                            if (printables.isEmpty()) {
//                                _uiState.value = _uiState.value.copy(isPrinting = false)
//                                updateConnectionStatus("Nothing to print")
//                                Log.w(TAG, "No content (text, image, or webpage) to print")
//                                return@launch
//                            }
//
//                            try {
//                                Log.d(TAG, "Sending print job with ${printables.size} items")
//                                printing?.print(printables)
//                            } catch (e: Exception) {
//                                updateConnectionStatus("Error printing: ${e.message}")
//                                Log.e(TAG, "Error printing: ${e.message}")
//                                _uiState.value = _uiState.value.copy(isPrinting = false)
//                            }
//                        }
//                    }
//                } catch (e: Exception) {
//                    updateConnectionStatus("Error processing webpage: ${e.message}")
//                    Log.e(TAG, "Error processing webpage: ${e.message}")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    return@launch
//                }
//            } else {
//                if (printables.isEmpty()) {
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                    updateConnectionStatus("Nothing to print")
//                    Log.w(TAG, "No content (text, image, or webpage) to print")
//                    return@launch
//                }
//                try {
//                    Log.d(TAG, "Sending print job with ${printables.size} items")
//                    printing?.print(printables)
//                } catch (e: Exception) {
//                    updateConnectionStatus("Error printing: ${e.message}")
//                    Log.e(TAG, "Error printing: ${e.message}")
//                    _uiState.value = _uiState.value.copy(isPrinting = false)
//                }
//            }
//        }
//    }
//
//    fun handleIntent(intent: Intent?) {
//        when (intent?.action) {
//            Intent.ACTION_SEND -> {
//                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
//                if (uri != null) {
//                    _uiState.value = _uiState.value.copy(sharedImageUri = uri, webpageUrl = "", textToPrint = "")
//                    Log.i(TAG, "Received shared image URI: $uri")
//                }
//                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
//                if (!text.isNullOrBlank()) {
//                    if (text.startsWith("http://") || text.startsWith("https://")) {
//                        _uiState.value = _uiState.value.copy(webpageUrl = text, sharedImageUri = null, textToPrint = "")
//                        Log.i(TAG, "Received shared webpage URL: $text")
//                    } else {
//                        _uiState.value = _uiState.value.copy(textToPrint = text, sharedImageUri = null, webpageUrl = "")
//                        Log.i(TAG, "Received shared text: $text")
//                    }
//                }
//            }
//        }
//    }
//
//    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
//        val aspectRatio = bitmap.height.toFloat() / bitmap.width
//        val targetHeight = (targetWidth * aspectRatio).toInt()
//        Log.d(TAG, "Resizing bitmap: original=${bitmap.width}x${bitmap.height}, target=${targetWidth}x$targetHeight")
//        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
//    }
//
//    @SuppressLint("MissingPermission")
//    fun connectToPrinter(context: Context, device: BluetoothDevice) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            updateConnectionStatus("Bluetooth permission denied")
//            Log.e(TAG, "Bluetooth permission denied when connecting to ${device.name}")
//            return
//        }
//        try {
//            if (printing != null && _uiState.value.connectedDevice == device) {
//                updateConnectionStatus("Already connected to ${device.name}")
//                Log.i(TAG, "Already connected to printer: ${device.name}")
//                return
//            }
//            Printooth.setPrinter(device.name, device.address)
//            printing = Printooth.printer()
//            setupPrintingCallback()
//            lastConnectedDevice = device
//            _uiState.value = _uiState.value.copy(
//                connectionStatus = "Connected to ${device.name}",
//                connectedDevice = device,
//                showSnackbar = true,
//                snackbarMessage = "Connected to ${device.name}",
//                isConnectionSuccess = true
//            )
//            Log.i(TAG, "Successfully connected to printer: ${device.name}, address: ${device.address}")
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Bluetooth permission error: ${e.message}")
//            Log.e(TAG, "Bluetooth permission error when connecting to ${device.name}: ${e.message}")
//        }
//    }
//
//    fun disconnectPrinter() {
//        printing?.let {
//            Printooth.removeCurrentPrinter()
//            printing = null
//            lastConnectedDevice = null
//            _uiState.value = _uiState.value.copy(
//                connectionStatus = "Printer disconnected",
//                connectedDevice = null,
//                isPrinting = false,
//                showSnackbar = true,
//                snackbarMessage = "Printer disconnected",
//                isConnectionSuccess = false
//            )
//            Log.i(TAG, "Printer disconnected successfully")
//        } ?: run {
//            updateConnectionStatus("No printer connected")
//            Log.w(TAG, "Disconnect attempted but no printer was connected")
//        }
//    }
//
//    private fun setupPrintingCallback() {
//        printing?.printingCallback = object : PrintingCallback {
//            override fun connectingWithPrinter() {
//                updateConnectionStatus("Connecting to printer...")
//                Log.d(TAG, "Connecting to printer...")
//            }
//
//            override fun connectionFailed(error: String) {
//                _uiState.value = _uiState.value.copy(
//                    connectionStatus = "Connection failed: $error",
//                    connectedDevice = null,
//                    isPrinting = false,
//                    showSnackbar = true,
//                    snackbarMessage = "Connection failed: $error",
//                    isConnectionSuccess = false
//                )
//                printing = null
//                Log.e(TAG, "Printer connection failed: $error")
//            }
//
//            override fun disconnected() {
//                _uiState.value = _uiState.value.copy(
//                    connectionStatus = "Printer disconnected",
//                    isPrinting = false,
//                    showSnackbar = true,
//                    snackbarMessage = "Printer disconnected",
//                    isConnectionSuccess = false
//                )
//                Log.i(TAG, "Printer disconnected")
//                lastConnectedDevice?.let { device ->
//                    appContext?.let { ctx ->
//                        viewModelScope.launch {
//                            Log.d(TAG, "Attempting to reconnect to ${device.name}")
//                            connectToPrinter(ctx, device)
//                        }
//                    } ?: run {
//                        _uiState.value = _uiState.value.copy(connectedDevice = null)
//                        printing = null
//                        Log.w(TAG, "No context available for reconnection")
//                    }
//                } ?: run {
//                    _uiState.value = _uiState.value.copy(connectedDevice = null)
//                    printing = null
//                    Log.w(TAG, "No last connected device for reconnection")
//                }
//            }
//
//            override fun onError(error: String) {
//                _uiState.value = _uiState.value.copy(
//                    connectionStatus = "Error: $error",
//                    isPrinting = false,
//                    showSnackbar = true,
//                    snackbarMessage = "Error: $error",
//                    isConnectionSuccess = false
//                )
//                Log.e(TAG, "Printer error: $error")
//            }
//
//            override fun onMessage(message: String) {
//                updateConnectionStatus(message)
//                Log.d(TAG, "Printer message: $message")
//            }
//
//            override fun printingOrderSentSuccessfully() {
//                _uiState.value = _uiState.value.copy(
//                    connectionStatus = "Print successful",
//                    isPrinting = false,
//                    showSnackbar = true,
//                    snackbarMessage = "Print successful",
//                    isConnectionSuccess = true
//                )
//                Log.i(TAG, "Print order sent successfully")
//            }
//        }
//    }
//
//    fun checkPermissionsAndBluetooth(context: Context) {
//        val permissions = arrayOf(
//            Manifest.permission.BLUETOOTH,
//            Manifest.permission.BLUETOOTH_ADMIN,
//            Manifest.permission.BLUETOOTH_CONNECT,
//            Manifest.permission.BLUETOOTH_SCAN,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.WRITE_EXTERNAL_STORAGE,
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        )
//
//        if (permissions.all {
//                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
//            }) {
//            checkBluetoothAndLoadDevices(context)
//        } else {
//            if (context is MainActivity) {
//                context.requestBluetoothPermission.launch(permissions)
//                Log.d(TAG, "Requesting Bluetooth permissions")
//            }
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    fun checkBluetoothAndLoadDevices(context: Context) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            updateConnectionStatus("Bluetooth permission denied")
//            Log.e(TAG, "Bluetooth permission denied when checking Bluetooth")
//            return
//        }
//        try {
//            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//            if (bluetoothAdapter == null) {
//                updateConnectionStatus("No Bluetooth adapter available")
//                Log.e(TAG, "No Bluetooth adapter available")
//                return
//            }
//            val isEnabled = bluetoothAdapter.isEnabled
//            _uiState.value = _uiState.value.copy(isBluetoothEnabled = isEnabled)
//            if (isEnabled) {
//                loadPairedDevices(context)
//                Log.d(TAG, "Bluetooth is enabled, loading paired devices")
//            } else {
//                updateConnectionStatus("Bluetooth is off")
//                Log.w(TAG, "Bluetooth is disabled")
//            }
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Bluetooth permission error: ${e.message}")
//            Log.e(TAG, "Bluetooth permission error: ${e.message}")
//        }
//    }
//
//    @SuppressLint("MissingPermission")
//    private fun loadPairedDevices(context: Context) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
//            updateConnectionStatus("Bluetooth permission denied")
//            Log.e(TAG, "Bluetooth permission denied when loading paired devices")
//            return
//        }
//        try {
//            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//            val devices = bluetoothAdapter.bondedDevices.toList()
//            _uiState.value = _uiState.value.copy(pairedDevices = devices)
//            if (devices.isEmpty()) {
//                updateConnectionStatus("No paired printers found")
//                Log.w(TAG, "No paired printers found")
//            } else {
//                Log.i(TAG, "Found ${devices.size} paired devices: ${devices.joinToString { it.name ?: "Unknown" }}")
//            }
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Bluetooth permission error: ${e.message}")
//            Log.e(TAG, "Bluetooth permission error when loading devices: ${e.message}")
//        }
//    }
//
//    fun enableBluetooth(context: MainActivity) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
//            updateConnectionStatus("Bluetooth permission denied")
//            Log.e(TAG, "Bluetooth permission denied when enabling Bluetooth")
//            return
//        }
//        try {
//            context.enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
//            Log.d(TAG, "Launching Bluetooth enable intent")
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Bluetooth permission error: ${e.message}")
//            Log.e(TAG, "Bluetooth permission error: ${e.message}")
//        }
//    }
//
//    fun scanForPrinters(context: MainActivity) {
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
//            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
//        ) {
//            updateConnectionStatus("Bluetooth scan or location permission denied")
//            Log.e(TAG, "Bluetooth scan or location permission denied")
//            return
//        }
//        try {
//            context.scanPrinter.launch(Intent(context, ScanningActivity::class.java))
//            Log.d(TAG, "Launching printer scan activity")
//        } catch (e: SecurityException) {
//            updateConnectionStatus("Permission error: ${e.message}")
//            Log.e(TAG, "Permission error when scanning for printers: ${e.message}")
//        }
//    }
//
//    fun onBluetoothEnabled(context: MainActivity) {
//        _uiState.value = _uiState.value.copy(isBluetoothEnabled = true)
//        loadPairedDevices(context)
//        Log.i(TAG, "Bluetooth enabled successfully")
//    }
//
//    fun onPrinterScanned(context: MainActivity) {
//        updateConnectionStatus("Printer paired successfully")
//        loadPairedDevices(context)
//        Log.i(TAG, "Printer paired successfully")
//    }
//
//    fun refreshDevices(context: Context) {
//        loadPairedDevices(context)
//        Log.d(TAG, "Refreshing paired devices")
//    }
//
//    internal fun updateConnectionStatus(status: String) {
//        val isSuccess = status.contains("Connected to") || status == "Print successful"
//        _uiState.value = _uiState.value.copy(
//            connectionStatus = status,
//            showSnackbar = true,
//            snackbarMessage = status,
//            isConnectionSuccess = isSuccess
//        )
//        Log.d(TAG, "Connection status updated: $status")
//    }
//
//    fun dismissSnackbar() {
//        _uiState.value = _uiState.value.copy(showSnackbar = false)
//        Log.d(TAG, "Snackbar dismissed")
//    }
//}