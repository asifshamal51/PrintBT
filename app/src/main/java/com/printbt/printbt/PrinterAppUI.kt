package com.printbt.printbt

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrinterAppUI(
    uiState: PrinterUiState,
    onConnectClick: (android.bluetooth.BluetoothDevice) -> Unit,
    onPrintClick: () -> Unit,
    onEnableBluetoothClick: () -> Unit,
    onScanClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Bluetooth Printer App",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Display shared image preview
        uiState.sharedImageUri?.let { uri ->
            val context = LocalContext.current
            val bitmap = remember(uri) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Image to print",
                    modifier = Modifier
                        .size(200.dp)
                        .padding(bottom = 16.dp)
                )
                Button(
                    onClick = onPrintClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.pairedDevices.isNotEmpty()
                ) {
                    Text("Print Image")
                }
            }
        }

        // Bluetooth status and controls
        Text(
            text = uiState.connectionStatus,
            color = if (uiState.connectionStatus.contains("Error") || uiState.connectionStatus.contains("failed") || uiState.connectionStatus.contains("denied")) Color.Red else Color.Green,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0))
                .padding(16.dp)
        )

        Button(
            onClick = onEnableBluetoothClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Enable Bluetooth")
        }

        Button(
            onClick = onScanClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Text("Scan for Printers")
        }

        // List of paired devices
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
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}