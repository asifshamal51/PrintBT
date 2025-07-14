package com.printbt.printbt

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
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
import androidx.compose.ui.viewinterop.AndroidView

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
    var webpageUrl by remember { mutableStateOf(uiState.webpageUrl) }

    LaunchedEffect(uiState.isPrinting) {
        Log.d("PrintPreviewScreen", "isPrinting: ${uiState.isPrinting}")
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with Close button and title
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

        // Scrollable content
        LazyColumn(
            modifier = Modifier
                .weight(1f) // Takes available space, leaving room for buttons
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show URL input and WebView only if webpage is shared
            if (uiState.webpageUrl.isNotBlank() && uiState.sharedImageUri == null && uiState.textToPrint.isBlank()) {
                item {
                    OutlinedTextField(
                        value = webpageUrl,
                        onValueChange = {
                            webpageUrl = it
                            viewModel?.updateWebpageUrl(it)
                        },
                        label = { Text("Enter webpage URL") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        singleLine = true
                    )
                }
                item {
                    AndroidView(
                        factory = {
                            WebView(it).apply {
                                settings.javaScriptEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.domStorageEnabled = true
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Mobile Safari/537.36"
                                loadUrl(uiState.webpageUrl)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(bottom = 16.dp)
                    )
                }
            }

            // Show text input only if plain text is shared
            if (uiState.textToPrint.isNotBlank() && uiState.sharedImageUri == null && uiState.webpageUrl.isBlank()) {
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
            }

            // Show image preview only if image is shared
            uiState.sharedImageUri?.let { uri ->
                if (uiState.webpageUrl.isBlank() && uiState.textToPrint.isBlank()) {
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

        // Fixed button section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            // Print size dropdown
            var expanded by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.fillMaxWidth()
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

            // Print button
            Button(
                onClick = {
                    if (!uiState.isPrinting) {
                        onPrintClick()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.connectedDevice != null &&
                        (uiState.sharedImageUri != null || uiState.textToPrint.isNotBlank() || uiState.webpageUrl.isNotBlank())
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

        // Connection status
        Text(
            text = uiState.connectionStatus,
            color = if (uiState.connectionStatus.contains("Error") ||
                uiState.connectionStatus.contains("failed") ||
                uiState.connectionStatus.contains("denied")) Color.Red else Color.Green,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0))
                .padding(16.dp)
        )
    }
}