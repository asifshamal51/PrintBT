// PrintPreviewActivity.kt
package com.printbt.printbt

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class PrintPreviewActivity : ComponentActivity() {
    private val viewModel: PrinterViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setContext(this)
        viewModel.handleIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState: State<PrinterUiState> = viewModel.uiState.collectAsState()
                    PrintPreviewScreen(
                        uiState = uiState.value,
                        onPrintClick = { viewModel.printImage(this@PrintPreviewActivity) },
                        onPrintSizeChange = { viewModel.setPrintSize(it) },
                        onBackClick = { finish() }
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

@Composable
fun PrintPreviewScreen(
    uiState: PrinterUiState,
    onPrintClick: () -> Unit,
    onPrintSizeChange: (PrintSize) -> Unit,
    onBackClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Print Preview",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }


        // Print size dropdown
        item {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(uiState.selectedPrintSize.label)
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    PrintSize.values().forEach { size ->
                        DropdownMenuItem(
                            text = { Text(size.label) },
                            onClick = {
                                onPrintSizeChange(size)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Print and Back buttons
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onBackClick,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text("Back")
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Button(
                        onClick = onPrintClick,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.connectedDevice != null && uiState.sharedImageUri != null
                    ) {
                    if (uiState.isPrinting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier
                                .size(24.dp)
                        )
                    } else {
                            Text("Print")
                        }
                    }
                }
            }

        }

        // Connection status
        item {
            Text(
                text = uiState.connectionStatus,
                color = if (uiState.connectionStatus.contains("Error") ||
                    uiState.connectionStatus.contains("failed") ||
                    uiState.connectionStatus.contains("denied")) Color.Red else Color.Green,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(Color(0xFFE0E0E0))
                    .padding(16.dp)
            )
        }

        // Image preview
        uiState.sharedImageUri?.let { uri ->
            item {
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
                            .fillMaxWidth()
                            .wrapContentHeight() // Ensure image height is not constrained
                            .aspectRatio(it.width.toFloat() / it.height)
                            .padding(bottom = 16.dp)
                    )
                } ?: Text(
                    text = "No image selected",
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}