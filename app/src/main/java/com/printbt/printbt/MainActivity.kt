package com.printbt.printbt

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.printbt.printbt.ui.theme.PrintBTTheme

class MainActivity : ComponentActivity() {
    internal val viewModel: PrinterViewModel by viewModels()

    internal val requestBluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d("MainActivity", "All permissions granted")
            viewModel.checkPermissionsAndBluetooth(this)
        } else {
            viewModel.updateConnectionStatus("Permissions denied: ${permissions.filter { !it.value }.keys.joinToString()}")
            Log.w("MainActivity", "Permissions denied: ${permissions.filter { !it.value }.keys.joinToString()}")
        }
    }

    internal val enableBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onBluetoothEnabled(this)
            Log.d("MainActivity", "Bluetooth enabled successfully")
        } else {
            viewModel.updateConnectionStatus("Bluetooth not enabled")
            Log.w("MainActivity", "Bluetooth not enabled")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setContext(this)
        handleIntent(intent)

        setContent {
            PrintBTTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsState()
                    MainScreen(
                        uiState = uiState,
                        onConnectClick = { device ->
                            viewModel.connectToPrinter(this, device)
                        },
                        onDisconnectClick = { viewModel.disconnectPrinter() },
                        onEnableBluetoothClick = { viewModel.enableBluetooth(this) },
                        onRefreshDevicesClick = { viewModel.refreshDevices(this) },
                        onPrintClick = { viewModel.printContent(this) },
                        onPrintSizeChange = { viewModel.setPrintSize(it) },
                        onDismissSnackbar = { viewModel.dismissSnackbar() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        viewModel.handleIntent(intent)
        Log.d("MainActivity", "Handling intent: ${intent?.action}")
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkPermissionsAndBluetooth(this)
        // Re-check printer connection
        val printService = (application as? PrintBTApplication)?.getPrintService()
        val selectedPrinter = printService?.selectedPrinter
        if (selectedPrinter != null && viewModel.getLastConnectedDevice()?.address != selectedPrinter.address) {
            viewModel.connectToPrinter(this, selectedPrinter)
            Log.d("MainActivity", "Restored printer from service: ${selectedPrinter.name}")
        } else if (viewModel.getLastConnectedDevice() != null) {
            viewModel.getLastConnectedDevice()?.let { device ->
                viewModel.connectToPrinter(this, device)
                Log.d("MainActivity", "Restored printer from ViewModel: ${device.name}")
            }
        }
    }
}
