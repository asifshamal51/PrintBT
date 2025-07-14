package com.printbt.printbt

import android.app.Application
import android.content.Intent
import android.printservice.PrintJob
import android.util.Log
import com.mazenrashed.printooth.Printooth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PrintBTApplication : Application() {
    private var printService: BluetoothPrintService? = null
    private val viewModelScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Printooth.init(this)
        Log.d("PrintBTApplication", "Application initialized")
    }

    fun setPrintService(service: BluetoothPrintService?) {
        printService = service
        Log.d("PrintBTApplication", "Print service set: ${service != null}")
    }

    fun getPrintService(): BluetoothPrintService? {
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
                    // Start the print job
                    printJob.start()
                    // TODO: Implement print job processing (e.g., convert to bitmap or ESC/POS)
                    // Example: val document = printJob.document
                    // val bitmap = convertPrintDocumentToBitmap(document.data)
                    // printing?.print(listOf(ImagePrintable.Builder(bitmap).build()))
                    printJob.complete()
                    Log.d("PrintBTApplication", "Print job completed")
                } else {
                    Log.w("PrintBTApplication", "Print job not in queued state: ${printJob.info.state}")
                    printJob.fail("Print job not queued")
                }
            } catch (e: Exception) {
                printJob.fail("Error processing print job: ${e.message}")
                Log.e("PrintBTApplication", "Error processing print job: ${e.message}")
            }
        }
    }
}

//package com.printbt.printbt
//
//import android.app.Application
//import android.printservice.PrintJob
//import android.util.Log
//import com.mazenrashed.printooth.Printooth
//
//class PrintBTApplication : Application() {
//    override fun onCreate() {
//        super.onCreate()
//        Printooth.init(this)
//    }
//
//    fun handlePrintJob(printJob: android.printservice.PrintJob) {
//        // Implement your actual printing logic here
//        Log.d("PrintBTApplication", "Handling print job: ${printJob.info.label}")
//
//        // Complete the print job when done
//        printJob.complete()
//
//        // Or if there's an error:
//        // printJob.fail("Error message")
//    }
//}