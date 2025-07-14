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

// PrintPreviewActivity.kt
class PrintPreviewActivity : ComponentActivity() {
    internal val viewModel: PrinterViewModel by viewModels()

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
                        onPrintClick = { viewModel.printContent(this@PrintPreviewActivity) }, // Updated to printContent
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

// PrintPreviewScreen.kt
@Composable
fun PrintPreviewScreen(
    uiState: PrinterUiState,
    onPrintClick: () -> Unit,
    onPrintSizeChange: (PrintSize) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel = (context as? PrintPreviewActivity)?.viewModel
    var textInput by remember { mutableStateOf(uiState.textToPrint) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Print Preview",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Text input field
        item {
            OutlinedTextField(
                value = textInput,
                onValueChange = {
                    textInput = it
                    viewModel?.updateTextToPrint(it)
                },
                label = { Text("Enter text to print") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                maxLines = 5
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

        // Print button
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Button(
                    onClick = onPrintClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.connectedDevice != null && (uiState.sharedImageUri != null || uiState.textToPrint.isNotBlank())
                ) {
                    if (uiState.isPrinting) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Text("Print")
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
                            .wrapContentHeight()
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