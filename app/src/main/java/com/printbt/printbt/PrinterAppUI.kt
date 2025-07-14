package com.printbt.printbt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PrinterAppUI(
    uiState: PrinterUiState,
    onConnectClick: (BluetoothDevice) -> Unit,
    onEnableBluetoothClick: () -> Unit,
    onRefreshClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onAddPrinterClick: () -> Unit,
    isAddPrinterMode: Boolean
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(uiState.showSnackbar, uiState.snackbarMessage) {
        if (uiState.showSnackbar) {
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = uiState.snackbarMessage,
                    duration = SnackbarDuration.Short
                )
                (context as? MainActivity)?.viewModel?.dismissSnackbar()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        containerColor = if (uiState.isConnectionSuccess) Color.Green else Color.Red,
                        contentColor = Color.White
                    ) {
                        Text(data.visuals.message)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isAddPrinterMode) "Add Bluetooth Printer" else "Bluetooth Printer App",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (uiState.connectionStatus.contains("permission denied", ignoreCase = true)) {
                Text(
                    text = "Please grant Bluetooth and Location permissions in Settings or retry below",
                    color = Color.Red,
                    modifier = Modifier.padding(8.dp)
                )
                Button(
                    onClick = { (context as? MainActivity)?.viewModel?.checkPermissionsAndBluetooth(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Retry Permissions")
                }
            }

            uiState.connectedDevice?.let { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Connected: ${device.name ?: "Unknown Device"}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = onDisconnectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Disconnect")
                        }
                    }
                }

                if (isAddPrinterMode) {
                    Button(
                        onClick = onAddPrinterClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                    ) {
                        Text("Add Printer")
                    }
                }
            }

            if (!uiState.isBluetoothEnabled) {
                Button(
                    onClick = onEnableBluetoothClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text("Enable Bluetooth")
                }
            }

            Button(
                onClick = onRefreshClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Text("Refresh")
            }

            Text(
                text = "Paired Printers",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            if (uiState.pairedDevices.isEmpty()) {
                Text(
                    text = "No paired printers found",
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            } else {
                LazyColumn {
                    items(uiState.pairedDevices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = device.name ?: "Unknown Device",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Button(
                                    onClick = { onConnectClick(device) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (uiState.connectedDevice == device)
                                            Color.Green else Color(0xFF6200EE)
                                    ),
                                    enabled = uiState.connectedDevice != device
                                ) {
                                    Text(if (uiState.connectedDevice == device) "Connected" else "Connect")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


