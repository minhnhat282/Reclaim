package com.minhnhat.reclaim

import android.app.Application
import android.content.Context

class ReclaimApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

/** Truy cập [AppGraph] từ bất kỳ Context nào (Service, Receiver, Activity...). */
val Context.graph: AppGraph
    get() = (applicationContext as ReclaimApp).graph
