package com.printbt.printbt

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mazenrashed.printooth.ui.ScanningActivity

class MainActivity : ComponentActivity() {
    internal val viewModel: PrinterViewModel by viewModels()
    private var isAddPrinterMode = false

    internal val requestBluetoothPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Log.d("MainActivity", "Permission result: $permissions")
            if (permissions.all { it.value }) {
                viewModel.checkBluetoothAndLoadDevices(this)
            } else {
                viewModel.updateConnectionStatus("Bluetooth permissions denied: ${permissions.filter { !it.value }.keys}")
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
        isAddPrinterMode = intent.action == "android.printservice.action.ADD_PRINTERS"
        Log.d("MainActivity", "Intent received: ${intent}, action: ${intent.action}, isAddPrinterMode: $isAddPrinterMode")

        if (isAddPrinterMode) {
            (application as? PrintBTApplication)?.startPrintService()
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PrinterAppUI(
                        uiState = viewModel.uiState.collectAsState().value,
                        onConnectClick = { device ->
                            viewModel.connectToPrinter(this, device)
                            if (isAddPrinterMode) {
                                (application as? PrintBTApplication)?.getPrintService()?.setSelectedPrinter(device)
                                setResult(Activity.RESULT_OK)
                                finish()
                            }
                        },
                        onEnableBluetoothClick = { viewModel.enableBluetooth(this) },
                        onRefreshClick = { viewModel.refreshDevices(this) },
                        onDisconnectClick = { viewModel.disconnectPrinter() },
                        onAddPrinterClick = {
                            viewModel.uiState.value.connectedDevice?.let { device ->
                                (application as? PrintBTApplication)?.getPrintService()?.setSelectedPrinter(device)
                                setResult(Activity.RESULT_OK)
                                finish()
                            } ?: run {
                                viewModel.updateConnectionStatus("No printer selected")
                            }
                        },
                        isAddPrinterMode = isAddPrinterMode
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleIntent(intent)
        isAddPrinterMode = intent.action == "android.printservice.action.ADD_PRINTERS"
        Log.d("MainActivity", "New intent: ${intent.action}, isAddPrinterMode: $isAddPrinterMode")
        if (isAddPrinterMode) {
            (application as? PrintBTApplication)?.startPrintService()
        }
    }
}

//package com.printbt.printbt
//
//import android.app.Activity
//import android.content.Intent
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.activity.viewModels
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.material3.MaterialTheme
//import androidx.compose.material3.Surface
//import androidx.compose.runtime.State
//import androidx.compose.runtime.collectAsState
//import androidx.compose.ui.Modifier
//import com.mazenrashed.printooth.Printooth
//import com.mazenrashed.printooth.ui.ScanningActivity
//
//class MainActivity : ComponentActivity() {
//    internal val viewModel: PrinterViewModel by viewModels()
//
//    internal val requestBluetoothPermission =
//        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
//            if (permissions.all { it.value }) {
//                viewModel.checkBluetoothAndLoadDevices(this)
//            } else {
//                viewModel.updateConnectionStatus("Bluetooth permissions denied")
//            }
//        }
//
//    internal val enableBluetooth =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                viewModel.onBluetoothEnabled(this)
//            } else {
//                viewModel.updateConnectionStatus("Bluetooth not enabled")
//            }
//        }
//
//    internal val scanPrinter =
//        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
//            if (result.resultCode == Activity.RESULT_OK) {
//                viewModel.onPrinterScanned(this)
//            }
//        }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        viewModel.setContext(this)
//        viewModel.checkPermissionsAndBluetooth(this)
//
//        setContent {
//            MaterialTheme {
//                Surface(modifier = Modifier.fillMaxSize()) {
//                    val uiState: State<PrinterUiState> = viewModel.uiState.collectAsState()
//                    PrinterAppUI(
//                        uiState = uiState.value,
//                        onConnectClick = { device -> viewModel.connectToPrinter(this, device) },
//                        onEnableBluetoothClick = { viewModel.enableBluetooth(this) },
//                        onRefreshClick = { viewModel.refreshDevices(this) },
//                        onDisconnectClick = { viewModel.disconnectPrinter() }
//                    )
//                }
//            }
//        }
//    }
//
//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//        viewModel.handleIntent(intent)
//    }
//}