package com.printbt.printbt

import android.bluetooth.BluetoothDevice
import android.graphics.BitmapFactory
import android.webkit.WebView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun MainScreen(
    uiState: PrinterUiState,
    onConnectClick: (BluetoothDevice) -> Unit,
    onDisconnectClick: () -> Unit,
    onEnableBluetoothClick: () -> Unit,
    onRefreshDevicesClick: () -> Unit,
    onPrintClick: () -> Unit,
    onPrintSizeChange: (PrintSize) -> Unit,
    onDismissSnackbar: () -> Unit
) {
    val context = LocalContext.current

    if (uiState.showSnackbar) {
        LaunchedEffect(uiState.snackbarMessage) {
            onDismissSnackbar()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Connection Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (uiState.connectedDevice != null) Color.Green.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.1f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiState.connectionStatus,
                    color = if (uiState.connectedDevice != null) Color.Green else Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                if (uiState.connectedDevice == null) {
                    Button(
                        onClick = onRefreshDevicesClick,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Refresh Devices")
                    }
                } else {
                    Button(
                        onClick = onDisconnectClick,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        // Bluetooth Toggle
        if (!uiState.isBluetoothEnabled) {
            Button(
                onClick = onEnableBluetoothClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Enable Bluetooth")
            }
        }

        // Paired Devices List
        if (uiState.isBluetoothEnabled && uiState.pairedDevices.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    items(uiState.pairedDevices) { device ->
                        Button(
                            onClick = { onConnectClick(device) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            enabled = uiState.connectedDevice?.address != device.address
                        ) {
                            Text(device.name ?: "Unknown Device")
                        }
                    }
                }
            }
        }

        // Print Size Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PrintSize.entries.forEach { size ->
                Button(
                    onClick = { onPrintSizeChange(size) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.selectedPrintSize == size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(size.name)
                }
            }
        }

        // Print Preview
        if (uiState.sharedImageUri != null || uiState.textToPrint.isNotBlank() || uiState.webpageUrl.isNotBlank() || uiState.printBitmaps.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Print Preview",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.sharedImageUri != null) {
                        val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uiState.sharedImageUri))
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Shared Image Preview",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }

                    if (uiState.textToPrint.isNotBlank()) {
                        Text(
                            text = uiState.textToPrint,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    if (uiState.webpageUrl.isNotBlank()) {
                        AndroidView(
                            factory = { WebView(it).apply { loadUrl(uiState.webpageUrl) } },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }

                    uiState.printBitmaps.forEach { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Print Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    }
                }
            }
        }

        // Print Button
        if (uiState.connectedDevice != null) {
            Button(
                onClick = onPrintClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isPrinting
            ) {
                Text("Print")
            }
        }
    }

    // Snackbar for connection status
    if (uiState.showSnackbar) {
        Snackbar(
            modifier = Modifier.padding(16.dp),
            action = {
                TextButton(onClick = onDismissSnackbar) {
                    Text("Dismiss")
                }
            }
        ) {
            Text(uiState.snackbarMessage)
        }
    }
}