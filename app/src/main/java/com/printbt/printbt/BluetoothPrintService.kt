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
    internal var selectedPrinter: BluetoothDevice? = null
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
