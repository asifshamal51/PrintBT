package com.printbt.printbt

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.mazenrashed.printooth.Printooth
import com.mazenrashed.printooth.ui.ScanningActivity

class MainActivity : ComponentActivity() {
    private val viewModel: PrinterViewModel by viewModels()

    internal val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions.all { it.value }) {
                viewModel.checkBluetoothAndLoadDevices(this)
            } else {
                viewModel.updateConnectionStatus("Bluetooth permissions denied")
            }
        }

    internal val enableBluetooth =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onBluetoothEnabled(this)
            } else {
                viewModel.updateConnectionStatus("Bluetooth not enabled")
            }
        }

    internal val scanPrinter =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                viewModel.onPrinterScanned(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setContext(this)
        viewModel.handleIntent(intent)
        viewModel.checkPermissionsAndBluetooth(this)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState: State<PrinterUiState> = viewModel.uiState.collectAsState()
                    PrinterAppUI(
                        uiState = uiState.value,
                        onConnectClick = { device -> viewModel.connectToPrinter(this, device) },
                        onPrintClick = { viewModel.printImage(this) },
                        onEnableBluetoothClick = { viewModel.enableBluetooth(this) },
                        onScanClick = { viewModel.scanForPrinters(this) }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
    }
}