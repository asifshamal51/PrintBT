package com.printbt.printbt

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.printservice.PrintJob
import android.util.Log
import androidx.annotation.RequiresApi
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothConnection
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import com.dantsu.escposprinter.textparser.PrinterTextParserImg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PrintBTApplication : Application() {
    private var printService: BluetoothPrintService? = null
    private val viewModelScope = CoroutineScope(Dispatchers.IO)
    private var bluetoothConnection: BluetoothConnection? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("PrintBTApplication", "Application initialized")
        createNotificationChannel()
        startPrintService()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "PrintBTServiceChannel",
                "PrintBT Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps printer connection active"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun setPrintService(service: BluetoothPrintService?) {
        printService = service
        Log.d("PrintBTApplication", "Print service set: ${service != null}")
        service?.let {
            val notification = Notification.Builder(this, "PrintBTServiceChannel")
                .setContentTitle("PrintBT Service")
                .setContentText("Maintaining printer connection")
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .build()
            it.startForeground(1, notification)
        }
    }

    fun getPrintService(): BluetoothPrintService? {
        if (printService == null) {
            Log.w("PrintBTApplication", "Print service is null, attempting to start")
            startPrintService()
        }
        Log.d("PrintBTApplication", "Retrieving print service: ${printService != null}")
        return printService
    }

    private fun startPrintService() {
        val intent = Intent(this, BluetoothPrintService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        Log.d("PrintBTApplication", "Started BluetoothPrintService with intent: $intent")
    }

    fun setBluetoothConnection(connection: BluetoothConnection?) {
        bluetoothConnection = connection
        Log.d("PrintBTApplication", "Bluetooth connection set: ${connection != null}")
    }

    fun getBluetoothConnection(): BluetoothConnection? {
        return bluetoothConnection
    }

    fun handlePrintJob(printJob: PrintJob) {
        Log.d("PrintBTApplication", "Handling print job: ${printJob.info.label}, state: ${printJob.info.state}")
        viewModelScope.launch(Dispatchers.Main) { // Switch to Main thread for PrintJob operations
            try {
                // Check if the job is in QUEUED or STARTED state
                if (printJob.isQueued || printJob.isStarted) {
                    if (printJob.isQueued) {
                        printJob.start() // Start the job if it's still queued
                        Log.d("PrintBTApplication", "Print job started: ${printJob.info.label}")
                    }
                    val document = printJob.document
                    val data = document?.data // ParcelFileDescriptor
                    if (data != null) {
                        val bitmap = withContext(Dispatchers.IO) { // Perform I/O operations on IO thread
                            convertPrintDocumentToBitmap(data)
                        }
                        if (bitmap != null) {
                            val printerConnection = bluetoothConnection ?: BluetoothPrintersConnections.selectFirstPaired()
                            if (printerConnection != null) {
                                if (!printerConnection.isConnected) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            Log.d("PrintBTApplication", "Attempting to reconnect to printer: ${printerConnection.device?.name}")
                                            printerConnection.connect()
                                            Log.d("PrintBTApplication", "Reconnected to printer: ${printerConnection.device?.name}")
                                        } catch (e: IOException) {
                                            printJob.fail("Failed to reconnect to printer: ${e.message}")
                                            Log.e("PrintBTApplication", "Failed to reconnect to printer: ${e.message}", e)
                                            return@withContext
                                        }
                                    }
                                }
                                val printer = EscPosPrinter(printerConnection, 203, 80f, 32)
                                withContext(Dispatchers.IO) { // Printing is I/O-bound
                                    try {
                                        // Test print
                                        printer.printFormattedTextAndCut("[C]Test Print\n")
                                        Log.d("PrintBTApplication", "Test print command sent successfully")
                                        // Convert bitmap to hexadecimal string
                                        val hexString = PrinterTextParserImg.bitmapToHexadecimalString(printer, bitmap)
                                        // Print the bitmap
                                        printer.printFormattedTextAndCut("[C]<img>$hexString</img>\n")
                                        Log.d("PrintBTApplication", "Bitmap print command sent successfully")
                                        // Cut paper
                                        printer.printFormattedTextAndCut("\n")
                                        Log.d("PrintBTApplication", "Paper cut command sent successfully")
                                    } catch (e: IOException) {
                                        printJob.fail("Failed to print: ${e.message}")
                                        Log.e("PrintBTApplication", "Failed to print: ${e.message}", e)
                                        return@withContext
                                    }
                                }
                                printJob.complete()
                                Log.d("PrintBTApplication", "Print job completed successfully")
                            } else {
                                printJob.fail("No paired printer found")
                                Log.w("PrintBTApplication", "No paired printer found")
                            }
                        } else {
                            printJob.fail("Failed to convert document to bitmap")
                            Log.w("PrintBTApplication", "Failed to convert document to bitmap")
                        }
                    } else {
                        printJob.fail("No document data available")
                        Log.w("PrintBTApplication", "No document data available")
                    }
                } else {
                    Log.w("PrintBTApplication", "Print job not in queued or started state: ${printJob.info.state}")
                    printJob.fail("Print job not in valid state: ${printJob.info.state}")
                }
            } catch (e: Exception) {
                printJob.fail("Error processing print job: ${e.message}")
                Log.e("PrintBTApplication", "Error processing print job: ${e.message}", e)
            }
        }
    }

    private fun convertPrintDocumentToBitmap(data: ParcelFileDescriptor?): Bitmap? {
        if (data == null) {
            Log.e("PrintBTApplication", "No file descriptor available for print document")
            return null
        }
        var tempFile: File? = null
        var tempFileDescriptor: ParcelFileDescriptor? = null
        return try {
            // Create a temporary file to make the descriptor seekable
            tempFile = File.createTempFile("print_doc", ".pdf", cacheDir)
            FileOutputStream(tempFile).use { out ->
                data.fileDescriptor?.let { fd ->
                    val buffer = ByteArray(8192)
                    val input = ParcelFileDescriptor.AutoCloseInputStream(data)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    input.close()
                }
            }
            // Open the temporary file as a seekable ParcelFileDescriptor
            tempFileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val pdfRenderer = PdfRenderer(tempFileDescriptor)
            if (pdfRenderer.pageCount == 0) {
                Log.e("PrintBTApplication", "PDF document has no pages")
                pdfRenderer.close()
                tempFileDescriptor.close()
                tempFile.delete()
                return null
            }
            val page = pdfRenderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            pdfRenderer.close()
            tempFileDescriptor.close()
            tempFile.delete()
            Log.d("PrintBTApplication", "Successfully converted document to bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Log.e("PrintBTApplication", "Error converting document to bitmap: ${e.message}", e)
            try {
                tempFileDescriptor?.close()
                tempFile?.delete()
            } catch (e: IOException) {
                Log.e("PrintBTApplication", "Error cleaning up temporary file: ${e.message}", e)
            }
            null
        }
    }
}