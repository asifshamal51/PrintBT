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
import androidx.compose.foundation.lazy.items // Correct import for items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap // Correct import for asImageBitmap
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

        // Sync with BluetoothPrintService and PrinterViewModel
        val printService = (application as? PrintBTApplication)?.getPrintService()
        val selectedPrinter = printService?.selectedPrinter
        if (selectedPrinter != null && viewModel.getLastConnectedDevice()?.address != selectedPrinter.address) {
            viewModel.connectToPrinter(this, selectedPrinter)
            Log.d("PrintPreviewActivity", "Initialized printer from service: ${selectedPrinter.name}")
        } else if (viewModel.getLastConnectedDevice() != null) {
            // Reconnect using last connected device
            viewModel.getLastConnectedDevice()?.let { device ->
                viewModel.connectToPrinter(this, device)
                Log.d("PrintPreviewActivity", "Restored printer from ViewModel: ${device.name}")
            }
        } else {
            Log.d("PrintPreviewActivity", "No selected printer in BluetoothPrintService or PrinterViewModel")
            viewModel.updateConnectionStatus("No printer connected. Please connect a printer.")
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState: State<PrinterUiState> = viewModel.uiState.collectAsState()
                    PrintPreviewScreen(
                        uiState = uiState.value,
                        onPrintClick = { viewModel.printContent(this@PrintPreviewActivity) },
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

    override fun onResume() {
        super.onResume()
        // Re-check printer connection on resume
        val printService = (application as? PrintBTApplication)?.getPrintService()
        val selectedPrinter = printService?.selectedPrinter
        if (selectedPrinter != null && viewModel.getLastConnectedDevice()?.address != selectedPrinter.address) {
            viewModel.connectToPrinter(this, selectedPrinter)
            Log.d("PrintPreviewActivity", "Reconnected printer from service on resume: ${selectedPrinter.name}")
        } else if (viewModel.getLastConnectedDevice() != null) {
            viewModel.getLastConnectedDevice()?.let { device ->
                viewModel.connectToPrinter(this, device)
                Log.d("PrintPreviewActivity", "Restored printer from ViewModel on resume: ${device.name}")
            }
        }
    }
}
//class PrintPreviewActivity : ComponentActivity() {
//    internal val viewModel: PrinterViewModel by viewModels()
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        viewModel.setContext(this)
//        viewModel.handleIntent(intent)
//
//        setContent {
//            MaterialTheme {
//                Surface(modifier = Modifier.fillMaxSize()) {
//                    val uiState: State<PrinterUiState> = viewModel.uiState.collectAsState()
//                    PrintPreviewScreen(
//                        uiState = uiState.value,
//                        onPrintClick = { viewModel.printContent(this@PrintPreviewActivity) },
//                        onPrintSizeChange = { viewModel.setPrintSize(it) },
//                        onBackClick = { finish() }
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

        // Display connection status prominently
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
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
                        onClick = {
                            // Navigate to MainActivity to reconnect
                            val intent = Intent(context, MainActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Connect Printer")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Show print job bitmaps if present
            if (uiState.printBitmaps.isNotEmpty()) {
                items(uiState.printBitmaps) { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Print job page",
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                            .padding(bottom = 16.dp)
                    )
                }
            }
            // Show URL input and WebView only if webpage is shared
            else if (uiState.webpageUrl.isNotBlank() && uiState.sharedImageUri == null && uiState.textToPrint.isBlank()) {
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
            else if (uiState.textToPrint.isNotBlank() && uiState.sharedImageUri == null && uiState.webpageUrl.isBlank()) {
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
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

            Button(
                onClick = {
//                    if (!uiState.isPrinting) {
                        onPrintClick()
//                    }
                },
                modifier = Modifier.fillMaxWidth(),
//                enabled = uiState.connectedDevice != null &&
//                        (uiState.sharedImageUri != null || uiState.textToPrint.isNotBlank() || uiState.webpageUrl.isNotBlank() || uiState.printBitmaps.isNotEmpty())
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
}

//@Composable
//fun PrintPreviewScreen(
//    uiState: PrinterUiState,
//    onPrintClick: () -> Unit,
//    onPrintSizeChange: (PrintSize) -> Unit,
//    onBackClick: () -> Unit
//) {
//    val context = LocalContext.current
//    val viewModel = (context as? PrintPreviewActivity)?.viewModel
//    var textInput by remember { mutableStateOf(uiState.textToPrint) }
//    var webpageUrl by remember { mutableStateOf(uiState.webpageUrl) }
//
//    LaunchedEffect(uiState.isPrinting) {
//        Log.d("PrintPreviewScreen", "isPrinting: ${uiState.isPrinting}")
//    }
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(bottom = 16.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            IconButton(
//                onClick = onBackClick,
//                modifier = Modifier.size(24.dp)
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Close,
//                    contentDescription = "Close",
//                    tint = MaterialTheme.colorScheme.primary
//                )
//            }
//            Text(
//                modifier = Modifier.fillMaxWidth(),
//                text = "Print Preview",
//                style = MaterialTheme.typography.headlineSmall,
//                fontWeight = FontWeight.Bold,
//                textAlign = TextAlign.Center
//            )
//        }
//
//        LazyColumn(
//            modifier = Modifier
//                .weight(1f)
//                .fillMaxWidth(),
//            horizontalAlignment = Alignment.CenterHorizontally
//        ) {
//            // Show print job bitmaps if present
//            if (uiState.printBitmaps.isNotEmpty()) {
//                items(uiState.printBitmaps) { bitmap -> // Use correct items function
//                    Image(
//                        bitmap = bitmap.asImageBitmap(), // Correct method with proper import
//                        contentDescription = "Print job page",
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .wrapContentHeight()
//                            .aspectRatio(bitmap.width.toFloat() / bitmap.height)
//                            .padding(bottom = 16.dp)
//                    )
//                }
//            }
//            // Show URL input and WebView only if webpage is shared
//            else if (uiState.webpageUrl.isNotBlank() && uiState.sharedImageUri == null && uiState.textToPrint.isBlank()) {
//                item {
//                    OutlinedTextField(
//                        value = webpageUrl,
//                        onValueChange = {
//                            webpageUrl = it
//                            viewModel?.updateWebpageUrl(it)
//                        },
//                        label = { Text("Enter webpage URL") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(bottom = 16.dp),
//                        singleLine = true
//                    )
//                }
//                item {
//                    AndroidView(
//                        factory = {
//                            WebView(it).apply {
//                                settings.javaScriptEnabled = true
//                                settings.useWideViewPort = true
//                                settings.loadWithOverviewMode = true
//                                settings.domStorageEnabled = true
//                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Mobile Safari/537.36"
//                                loadUrl(uiState.webpageUrl)
//                            }
//                        },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .heightIn(max = 300.dp)
//                            .padding(bottom = 16.dp)
//                    )
//                }
//            }
//            // Show text input only if plain text is shared
//            else if (uiState.textToPrint.isNotBlank() && uiState.sharedImageUri == null && uiState.webpageUrl.isBlank()) {
//                item {
//                    OutlinedTextField(
//                        value = textInput,
//                        onValueChange = {
//                            textInput = it
//                            viewModel?.updateTextToPrint(it)
//                        },
//                        label = { Text("Enter text to print") },
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .padding(bottom = 16.dp),
//                        maxLines = 5
//                    )
//                }
//            }
//            // Show image preview only if image is shared
//            uiState.sharedImageUri?.let { uri ->
//                if (uiState.webpageUrl.isBlank() && uiState.textToPrint.isBlank()) {
//                    item {
//                        val bitmap = remember(uri) {
//                            try {
//                                val inputStream = context.contentResolver.openInputStream(uri)
//                                val bmp = BitmapFactory.decodeStream(inputStream)
//                                inputStream?.close()
//                                bmp
//                            } catch (e: Exception) {
//                                null
//                            }
//                        }
//                        bitmap?.let {
//                            Image(
//                                bitmap = it.asImageBitmap(),
//                                contentDescription = "Image to print",
//                                modifier = Modifier
//                                    .fillMaxWidth()
//                                    .wrapContentHeight()
//                                    .aspectRatio(it.width.toFloat() / it.height)
//                                    .padding(bottom = 16.dp)
//                            )
//                        } ?: Text(
//                            text = "No image selected",
//                            color = Color.Red,
//                            modifier = Modifier.padding(bottom = 16.dp)
//                        )
//                    }
//                }
//            }
//        }
//
//        Column(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 16.dp)
//        ) {
//            var expanded by remember { mutableStateOf(false) }
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(bottom = 8.dp)
//            ) {
//                OutlinedButton(
//                    onClick = { expanded = true },
//                    modifier = Modifier.fillMaxWidth()
//                ) {
//                    Text(uiState.selectedPrintSize.label)
//                }
//                DropdownMenu(
//                    expanded = expanded,
//                    onDismissRequest = { expanded = false }
//                ) {
//                    PrintSize.values().forEach { size ->
//                        DropdownMenuItem(
//                            text = { Text(size.label) },
//                            onClick = {
//                                onPrintSizeChange(size)
//                                expanded = false
//                            }
//                        )
//                    }
//                }
//            }
//
//            Button(
//                onClick = {
//                    if (!uiState.isPrinting) {
//                        onPrintClick()
//                    }
//                },
//                modifier = Modifier.fillMaxWidth(),
//                enabled = uiState.connectedDevice != null &&
//                        (uiState.sharedImageUri != null || uiState.textToPrint.isNotBlank() || uiState.webpageUrl.isNotBlank() || uiState.printBitmaps.isNotEmpty())
//            ) {
//                if (uiState.isPrinting) {
//                    CircularProgressIndicator(
//                        color = Color.White,
//                        modifier = Modifier.size(24.dp)
//                    )
//                } else {
//                    Text("Print")
//                }
//            }
//        }
//
//        Text(
//            text = uiState.connectionStatus,
//            color = if (uiState.connectionStatus.contains("Error") ||
//                uiState.connectionStatus.contains("failed") ||
//                uiState.connectionStatus.contains("denied")) Color.Red else Color.Green,
//            modifier = Modifier
//                .fillMaxWidth()
//                .background(Color(0xFFE0E0E0))
//                .padding(16.dp)
//        )
//    }
//}