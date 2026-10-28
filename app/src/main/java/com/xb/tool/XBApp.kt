package com.xb.tool

import android.app.Application
import com.xb.tool.guard.Notifications

class XBApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
