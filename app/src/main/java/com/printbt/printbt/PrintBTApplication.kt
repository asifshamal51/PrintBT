package com.printbt.printbt

import android.app.Application
import com.mazenrashed.printooth.Printooth

class PrintBTApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Printooth.init(this)
    }
}