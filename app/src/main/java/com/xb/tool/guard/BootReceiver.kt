package com.xb.tool.guard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xb.tool.core.LogBus
import com.xb.tool.core.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        if (!prefs.keepAlive && !prefs.realtimeEnabled) return
        runCatching {
            GuardService.start(context)
            if (prefs.extremeKeepAlive) WatchdogService.start(context)
            LogBus.info("开机守护已拉起")
        }.onFailure {
            LogBus.warn("开机自启失败：${it.message}")
        }
    }
}
