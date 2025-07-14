package com.printbt.printbt

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.printservice.PrintJob
import android.util.Log
import com.dantsu.escposprinter.EscPosPrinter
import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrintBTApplication : Application() {
    private var printService: BluetoothPrintService? = null
    private val viewModelScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d("PrintBTApplication", "Application initialized")
        startPrintService() // Start service immediately
    }

    fun setPrintService(service: BluetoothPrintService?) {
        printService = service
        Log.d("PrintBTApplication", "Print service set: ${service != null}")
    }

    fun getPrintService(): BluetoothPrintService? {
        if (printService == null) {
            Log.w("PrintBTApplication", "Print service is null, attempting to start")
            startPrintService()
        }
        Log.d("PrintBTApplication", "Retrieving print service: ${printService != null}")
        return printService
    }

    fun startPrintService() {
        val intent = Intent(this, BluetoothPrintService::class.java)
        val result = startService(intent)
        Log.d("PrintBTApplication", "Started BluetoothPrintService with intent: $intent, result: $result")
    }

    fun handlePrintJob(printJob: PrintJob) {
        Log.d("PrintBTApplication", "Handling print job: ${printJob.info.label}")
        viewModelScope.launch {
            try {
                if (printJob.isQueued) {
                    printJob.start()
                    val document = printJob.document
                    val data = document?.data // ParcelFileDescriptor
                    if (data != null) {
                        val bitmap = convertPrintDocumentToBitmap(data)
                        if (bitmap != null) {
                            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
                            if (printerConnection != null) {
                                val printer = EscPosPrinter(printerConnection, 203, 80f, 32)
                                printer.printFormattedTextAndCut("[C]<img>$bitmap</img>\n")
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
                    Log.w("PrintBTApplication", "Print job not in queued state: ${printJob.info.state}")
                    printJob.fail("Print job not queued")
                }
            } catch (e: Exception) {
                printJob.fail("Error processing print job: ${e.message}")
                Log.e("PrintBTApplication", "Error processing print job: ${e.message}", e)
            }
        }
    }

    private fun convertPrintDocumentToBitmap(data: ParcelFileDescriptor?): Bitmap? {
        if (data == null) {
            Log.e("PrintBTApplication", "No file descriptor available")
            return null
        }
        return try {
            val pdfRenderer = PdfRenderer(data)
            val page = pdfRenderer.openPage(0)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
            page.close()
            pdfRenderer.close()
            data.close()
            Log.d("PrintBTApplication", "Successfully converted document to bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Log.e("PrintBTApplication", "Error converting document to bitmap: ${e.message}", e)
            null
        }
    }
}

//package com.printbt.printbt
//
//import android.app.Application
//import android.content.Intent
//import android.graphics.Bitmap
//import android.graphics.pdf.PdfRenderer
//import android.os.ParcelFileDescriptor
//import android.printservice.PrintJob
//import android.util.Log
//import com.dantsu.escposprinter.EscPosPrinter
//import com.dantsu.escposprinter.connection.bluetooth.BluetoothPrintersConnections
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//
//class PrintBTApplication : Application() {
//    private var printService: BluetoothPrintService? = null
//    private val viewModelScope = CoroutineScope(Dispatchers.IO)
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d("PrintBTApplication", "Application initialized")
//    }
//
//    fun setPrintService(service: BluetoothPrintService?) {
//        printService = service
//        Log.d("PrintBTApplication", "Print service set: ${service != null}")
//    }
//
//    fun getPrintService(): BluetoothPrintService? {
//        Log.d("PrintBTApplication", "Retrieving print service: ${printService != null}")
//        return printService
//    }
//
//    fun startPrintService() {
//        val intent = Intent(this, BluetoothPrintService::class.java)
//        val result = startService(intent)
//        Log.d("PrintBTApplication", "Started BluetoothPrintService with intent: $intent, result: $result")
//    }
//
//    fun handlePrintJob(printJob: PrintJob) {
//        Log.d("PrintBTApplication", "Handling print job: ${printJob.info.label}")
//        viewModelScope.launch {
//            try {
//                if (printJob.isQueued) {
//                    printJob.start()
//                    val document = printJob.document
//                    val data = document?.data // ParcelFileDescriptor
//                    if (data != null) {
//                        val bitmap = convertPrintDocumentToBitmap(data)
//                        if (bitmap != null) {
//                            val printerConnection = BluetoothPrintersConnections.selectFirstPaired()
//                            if (printerConnection != null) {
//                                val printer = EscPosPrinter(printerConnection, 203, 80f, 32)
//                                printer.printFormattedTextAndCut("[C]<img>$bitmap</img>\n")
//                                printJob.complete()
//                                Log.d("PrintBTApplication", "Print job completed")
//                            } else {
//                                printJob.fail("No paired printer found")
//                                Log.w("PrintBTApplication", "No paired printer found")
//                            }
//                        } else {
//                            printJob.fail("Failed to convert document to bitmap")
//                            Log.w("PrintBTApplication", "Failed to convert document to bitmap")
//                        }
//                    } else {
//                        printJob.fail("No document data available")
//                        Log.w("PrintBTApplication", "No document data available")
//                    }
//                } else {
//                    Log.w("PrintBTApplication", "Print job not in queued state: ${printJob.info.state}")
//                    printJob.fail("Print job not queued")
//                }
//            } catch (e: Exception) {
//                printJob.fail("Error processing print job: ${e.message}")
//                Log.e("PrintBTApplication", "Error processing print job: ${e.message}")
//            }
//        }
//    }
//
//    private fun convertPrintDocumentToBitmap(data: ParcelFileDescriptor?): Bitmap? {
//        if (data == null) {
//            Log.e("PrintBTApplication", "No file descriptor available")
//            return null
//        }
//        return try {
//            val pdfRenderer = PdfRenderer(data)
//            val page = pdfRenderer.openPage(0)
//            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
//            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
//            page.close()
//            pdfRenderer.close()
//            data.close()
//            bitmap
//        } catch (e: Exception) {
//            Log.e("PrintBTApplication", "Error converting document to bitmap: ${e.message}")
//            null
//        }
//    }
//}
