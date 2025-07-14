package com.printbt.printbt

import android.bluetooth.BluetoothDevice
import android.print.PrintAttributes
import android.print.PrinterCapabilitiesInfo
import android.print.PrinterId
import android.print.PrinterInfo
import android.printservice.PrintJob
import android.printservice.PrintService
import android.printservice.PrinterDiscoverySession
import android.util.Log

class BluetoothPrintService : PrintService() {
    private val TAG = "BluetoothPrintService"
    private var selectedPrinter: BluetoothDevice? = null
    private var currentSession: PrinterDiscoverySession? = null

    override fun onCreate() {
        super.onCreate()
        (application as? PrintBTApplication)?.setPrintService(this)
        Log.d(TAG, "BluetoothPrintService created")
    }

    fun setSelectedPrinter(device: BluetoothDevice?) {
        selectedPrinter = device
        Log.d(TAG, "Selected printer set: ${device?.name ?: "None"}")
        currentSession?.let { session ->
            Log.d(TAG, "Refreshing existing printer discovery session")
            session.onStartPrinterDiscovery(mutableListOf())
        } ?: run {
            Log.d(TAG, "No active session, creating new one")
            val newSession = onCreatePrinterDiscoverySession()
            newSession.onStartPrinterDiscovery(mutableListOf())
            currentSession = newSession
        }
    }

    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
        return object : PrinterDiscoverySession() {
            override fun onStartPrinterDiscovery(priorityList: MutableList<PrinterId>) {
                Log.d(TAG, "Starting printer discovery")
                val printers = mutableListOf<PrinterInfo>()

                selectedPrinter?.let { device ->
                    val printerId = generatePrinterId(device.address)
                    val printerInfo = PrinterInfo.Builder(
                        printerId,
                        device.name ?: "Bluetooth Printer",
                        PrinterInfo.STATUS_IDLE
                    )
                        .setDescription("Print via Bluetooth to ${device.name}")
                        .setCapabilities(createPrinterCapabilities())
                        .build()
                    printers.add(printerInfo)
                    Log.d(TAG, "Added printer: ${device.name}, address: ${device.address}")
                } ?: run {
                    Log.d(TAG, "No selected printer, adding default")
                    val printerId = generatePrinterId("bluetooth_printer")
                    val printerInfo = PrinterInfo.Builder(
                        printerId,
                        "Bluetooth Printer",
                        PrinterInfo.STATUS_IDLE
                    )
                        .setDescription("Print via Bluetooth")
                        .setCapabilities(createPrinterCapabilities())
                        .build()
                    printers.add(printerInfo)
                }

                addPrinters(printers)
            }

            override fun onStopPrinterDiscovery() {
                Log.d(TAG, "Stopping printer discovery")
            }

            override fun onValidatePrinters(printerIds: MutableList<PrinterId>) {
                Log.d(TAG, "Validating printers: ${printerIds.size}")
            }

            override fun onStartPrinterStateTracking(printerId: PrinterId) {
                Log.d(TAG, "Starting state tracking for $printerId")
                val printerInfo = PrinterInfo.Builder(
                    printerId,
                    printerId.localId,
                    PrinterInfo.STATUS_IDLE
                ).build()
                addPrinters(listOf(printerInfo))
            }

            override fun onStopPrinterStateTracking(printerId: PrinterId) {
                Log.d(TAG, "Stopping state tracking for $printerId")
            }

            override fun onDestroy() {
                Log.d(TAG, "Printer discovery session destroyed")
                currentSession = null
            }
        }.also { session ->
            currentSession = session
            Log.d(TAG, "New printer discovery session created")
        }
    }

    private fun createPrinterCapabilities(): PrinterCapabilitiesInfo {
        return PrinterCapabilitiesInfo.Builder(generatePrinterId("default"))
            .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
            .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
            .addResolution(
                PrintAttributes.Resolution("default", "Default", 300, 300),
                true
            )
            .setColorModes(
                PrintAttributes.COLOR_MODE_MONOCHROME,
                PrintAttributes.COLOR_MODE_MONOCHROME
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
    }

    override fun onRequestCancelPrintJob(printJob: PrintJob) {
        printJob.cancel()
        Log.d(TAG, "Print job cancelled: ${printJob.info.label}")
    }

    override fun onPrintJobQueued(printJob: PrintJob) {
        printJob.start()
        (application as? PrintBTApplication)?.handlePrintJob(printJob)
        Log.d(TAG, "Print job queued: ${printJob.info.label}")
    }
}

//package com.printbt.printbt
//
//import android.Manifest
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.BluetoothDevice
//import android.content.Context
//import android.graphics.Bitmap
//import android.graphics.pdf.PdfRenderer
//import android.os.ParcelFileDescriptor
//import android.print.PrinterId
//import android.print.PrinterInfo
//import android.printservice.PrintJob
//import android.printservice.PrintService
//import android.printservice.PrinterDiscoverySession
//import android.util.Log
//import androidx.annotation.RequiresPermission
//import com.mazenrashed.printooth.Printooth
//import com.mazenrashed.printooth.data.printable.ImagePrintable
//import com.mazenrashed.printooth.utilities.Printing
//import com.mazenrashed.printooth.utilities.PrintingCallback
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import java.io.File
//
//class BluetoothPrintService : PrintService() {
//    private val TAG = "BluetoothPrintService"
//    private lateinit var viewModel: PrinterViewModel
//    private var printing: Printing? = null
//
//    override fun onCreate() {
//        super.onCreate()
//        viewModel = PrinterViewModel().apply { setContext(this@BluetoothPrintService) }
//        Printooth.init(this)
//        printing = Printooth.printer()
//        Log.d(TAG, "BluetoothPrintService created")
//    }
//
//    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession {
//        return object : PrinterDiscoverySession() {
//            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
//            override fun onStartPrinterDiscovery(priorityList: List<PrinterId>) {
//                val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//                if (bluetoothAdapter == null) {
//                    Log.e(TAG, "Bluetooth not supported on this device")
//                    return
//                }
//
//                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
//                val printers = mutableListOf<PrinterInfo>()
//                pairedDevices?.forEach { device ->
//                    if (device.name.contains("printer", ignoreCase = true)) {
//                        val printerId = generatePrinterId(device.address)
//                        val printerInfo = PrinterInfo.Builder(
//                            printerId,
//                            device.name,
//                            PrinterInfo.STATUS_IDLE
//                        )
//                            .setDescription("Bluetooth Printer: ${device.name}")
//                            .setCapabilities(createPrinterCapabilities())
//                            .build()
//                        printers.add(printerInfo)
//                    }
//                }
//
//                addPrinters(printers)
//                Log.d(TAG, "Discovered ${printers.size} Bluetooth printers")
//            }
//
//            override fun onStopPrinterDiscovery() {
//                // No action needed
//            }
//
//            override fun onValidatePrinters(printerIds: List<android.print.PrinterId>) {
//                // Validate printers if needed
//            }
//
//            override fun onStartPrinterStateTracking(printerId: android.print.PrinterId) {
//                val printerInfo = android.print.PrinterInfo.Builder(
//                    printerId,
//                    printerId.localId,
//                    android.print.PrinterInfo.STATUS_IDLE
//                ).build()
//                addPrinters(listOf(printerInfo))
//            }
//
//            override fun onStopPrinterStateTracking(printerId: android.print.PrinterId) {
//                // No action needed
//            }
//
//            override fun onDestroy() {
//                // Clean up resources if needed
//            }
//        }
//    }
//
//    private fun createPrinterCapabilities(): android.print.PrinterCapabilitiesInfo {
//        return android.print.PrinterCapabilitiesInfo.Builder(generatePrinterId("default"))
//            .addMediaSize(android.print.PrintAttributes.MediaSize.ISO_A4, true)
//            .addResolution(android.print.PrintAttributes.Resolution("default", "Default", 300, 300), true)
//            .setColorModes(
//                android.print.PrintAttributes.COLOR_MODE_MONOCHROME or android.print.PrintAttributes.COLOR_MODE_COLOR,
//                android.print.PrintAttributes.COLOR_MODE_MONOCHROME
//            )
//            .setMinMargins(android.print.PrintAttributes.Margins(0, 0, 0, 0))
//            .build()
//    }
//
//    override fun onPrintJobQueued(printJob: PrintJob) {
//        Log.d(TAG, "Print job queued: ${printJob.info.label}")
//        handlePrintJob(printJob)
//    }
//
//    override fun onRequestCancelPrintJob(printJob: PrintJob) {
//        Log.d(TAG, "Print job cancellation requested: ${printJob.info.label}")
//        printJob.cancel()
//    }
//
//    private fun handlePrintJob(printJob: PrintJob) {
//        if (!printJob.isQueued) {
//            Log.e(TAG, "Print job is not in queued state")
//            printJob.fail("Print job is not in queued state")
//            return
//        }
//
//        val outputFile = File(cacheDir, "print_job_${printJob.id.toString()}.pdf")
//        val fileDescriptor = try {
//            ParcelFileDescriptor.open(
//                outputFile,
//                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
//            )
//        } catch (e: Exception) {
//            Log.e(TAG, "Failed to create file descriptor: ${e.message}")
//            printJob.fail("Failed to create file descriptor: ${e.message}")
//            return
//        }
//
//        try {
//            fileDescriptor.close()
//            processPrintJob(outputFile, printJob)
//        } catch (e: Exception) {
//            Log.e(TAG, "Error processing print job: ${e.message}")
//            printJob.fail("Error processing print job: ${e.message}")
//        }
//    }
//
//    private fun processPrintJob(pdfFile: File, printJob: PrintJob) {
//        CoroutineScope(Dispatchers.IO).launch {
//            try {
//                val readDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
//                val renderer = PdfRenderer(readDescriptor)
//                val pageCount = renderer.pageCount
//                val bitmaps = mutableListOf<Bitmap>()
//                for (i in 0 until pageCount) {
//                    val page = renderer.openPage(i)
//                    val bitmap = Bitmap.createBitmap(
//                        page.width,
//                        page.height,
//                        Bitmap.Config.ARGB_8888
//                    )
//                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
//                    bitmaps.add(bitmap)
//                    page.close()
//                }
//                renderer.close()
//                readDescriptor.close()
//
//                viewModel.updatePrintBitmaps(bitmaps)
//
//                printing?.apply {
//                    printingCallback = object : PrintingCallback {
//                        override fun connectingWithPrinter() {
//                            Log.d(TAG, "Connecting to printer")
//                        }
//
//                        override fun connectionFailed(error: String) {
//                            Log.e(TAG, "Connection failed: $error")
//                            printJob.fail("Printer connection failed: $error")
//                        }
//
//                        override fun disconnected() {
//                            Log.d(TAG, "Printer disconnected")
//                        }
//
//                        override fun onError(error: String) {
//                            Log.e(TAG, "Print error: $error")
//                            printJob.fail("Print error: $error")
//                        }
//
//                        override fun onMessage(message: String) {
//                            Log.d(TAG, "Print message: $message")
//                        }
//
//                        override fun printingOrderSentSuccessfully() {
//                            Log.i(TAG, "Print job sent successfully")
//                            printJob.complete()
//                        }
//                    }
//                    // Corrected ImagePrintable usage
//                    print(bitmaps.map { ImagePrintable.Builder(it).build() })
//                } ?: run {
//                    Log.e(TAG, "Printer not initialized")
//                    printJob.fail("Printer not initialized")
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error processing print job: ${e.message}")
//                printJob.fail("Error processing print job: ${e.message}")
//            } finally {
//                if (pdfFile.exists()) {
//                    pdfFile.delete()
//                }
//            }
//        }
//    }
//}
////package com.printbt.printbt
////
////import android.content.Context
////import android.graphics.Bitmap
////import android.graphics.pdf.PdfRenderer
////import android.os.ParcelFileDescriptor
////import android.printservice.PrintJob
////import android.printservice.PrintService
////import android.printservice.PrinterDiscoverySession
////import android.util.Log
////import kotlinx.coroutines.CoroutineScope
////import kotlinx.coroutines.Dispatchers
////import kotlinx.coroutines.launch
////import java.io.File
////import java.io.FileOutputStream
////
////class BluetoothPrintService : PrintService() {
////    private val TAG = "BluetoothPrintService"
////    private lateinit var viewModel: PrinterViewModel
////
////    override fun onCreate() {
////        super.onCreate()
////        viewModel = PrinterViewModel().apply { setContext(this@BluetoothPrintService) }
////        Log.d(TAG, "BluetoothPrintService created")
////    }
////
////    override fun onCreatePrinterDiscoverySession(): PrinterDiscoverySession? {
////        return null // No network printer discovery needed for Bluetooth
////    }
////
////    override fun onPrintJobQueued(printJob: PrintJob) {
////        Log.d(TAG, "Print job queued: ${printJob.info.label}")
////        handlePrintJob(printJob)
////    }
////
////    override fun onRequestCancelPrintJob(printJob: PrintJob) {
////        Log.d(TAG, "Print job cancellation requested: ${printJob.info.label}")
////        printJob.cancel()
////    }
////
////    private fun handlePrintJob(printJob: PrintJob) {
////        // Check if the print job is in a valid state
////        if (!printJob.isQueued) {
////            Log.e(TAG, "Print job is not in queued state")
////            printJob.fail("Print job is not in queued state")
////            return
////        }
////
////        // Create a temporary file for the print job
////        val outputFile = File(cacheDir, "print_job_${printJob.id.toString()}.pdf")
////        val fileDescriptor = try {
////            ParcelFileDescriptor.open(
////                outputFile,
////                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
////            )
////        } catch (e: Exception) {
////            Log.e(TAG, "Failed to create file descriptor: ${e.message}")
////            printJob.fail("Failed to create file descriptor: ${e.message}")
////            return
////        }
////
////        // Since we cannot directly access the PrintDocumentAdapter, we assume the print job
////        // provides a PDF file through the file descriptor. This is a common approach for print services.
////        // For simplicity, we'll copy the document data to the file descriptor.
////        try {
////            // Note: The actual document data retrieval may depend on the client app (e.g., Chrome).
////            // Here, we assume the print job provides a PDF file descriptor.
////            // In a real-world scenario, you may need to interact with the PrintDocumentAdapter
////            // indirectly through a custom PrintDocumentAdapter in your app.
////            processPrintJob(outputFile, printJob, fileDescriptor)
////        } catch (e: Exception) {
////            Log.e(TAG, "Error processing print job: ${e.message}")
////            printJob.fail("Error processing print job: ${e.message}")
////            try {
////                fileDescriptor.close()
////            } catch (e: Exception) {
////                Log.e(TAG, "Error closing file descriptor: ${e.message}")
////            }
////        }
////    }
////
////    private fun processPrintJob(pdfFile: File, printJob: PrintJob, fileDescriptor: ParcelFileDescriptor) {
////        CoroutineScope(Dispatchers.IO).launch {
////            try {
////                // Since direct access to the PrintDocumentAdapter is not possible,
////                // we assume the client app (e.g., Chrome) has written the PDF to the file descriptor.
////                // In a complete implementation, you may need to integrate with a custom PrintDocumentAdapter
////                // provided by your app to handle the document data.
////
////                // For this example, we'll assume the file descriptor contains a valid PDF.
////                fileDescriptor.close() // Close the write descriptor
////
////                // Open the file for reading
////                val readDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
////                val renderer = PdfRenderer(readDescriptor)
////                val pageCount = renderer.pageCount
////                val bitmaps = mutableListOf<Bitmap>()
////                for (i in 0 until pageCount) {
////                    val page = renderer.openPage(i)
////                    val bitmap = Bitmap.createBitmap(
////                        page.width,
////                        page.height,
////                        Bitmap.Config.ARGB_8888
////                    )
////                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
////                    bitmaps.add(bitmap)
////                    page.close()
////                }
////                renderer.close()
////                readDescriptor.close()
////
////                // Update ViewModel with bitmaps for printing
////                viewModel.updatePrintBitmaps(bitmaps)
////                viewModel.printContent(this@BluetoothPrintService)
////
////                printJob.complete()
////                Log.i(TAG, "Print job completed successfully")
////            } catch (e: Exception) {
////                Log.e(TAG, "Error processing print job: ${e.message}")
////                printJob.fail("Error processing print job: ${e.message}")
////            } finally {
////                // Clean up the temporary file
////                if (pdfFile.exists()) {
////                    pdfFile.delete()
////                }
////            }
////        }
////    }
////}