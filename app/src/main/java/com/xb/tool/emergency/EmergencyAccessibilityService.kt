package com.xb.tool.emergency

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.xb.tool.core.LogBus

/**
 * 紧急救机的无障碍服务：用于在任意界面监听音量键，作为救援触发方式。
 *
 * 普通应用无法在后台截获音量键，无障碍服务可以。这里只监听音量键按下，
 * 不读取屏幕内容、不执行任何无障碍操作。
 */
class EmergencyAccessibilityService : AccessibilityService() {

    private val pressTimes = mutableListOf<Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        EmergencyNotifier.setAccessibilityConnected(this, true)
        LogBus.ok("紧急救机无障碍服务已连接，可监听音量键")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (!EmergencyNotifier.isEnabled(this)) return false
        if (!EmergencyNotifier.isButtonEnabled(this)) return false

        val expected = when (EmergencyNotifier.buttonKey(this)) {
            "volume_down" -> KeyEvent.KEYCODE_VOLUME_DOWN
            else -> KeyEvent.KEYCODE_VOLUME_UP
        }
        if (event.keyCode != expected) return false

        val now = System.currentTimeMillis()
        synchronized(pressTimes) {
            pressTimes += now
            // 连按窗口：1.5 秒
            pressTimes.removeAll { now - it > 1_500L }
            val need = EmergencyNotifier.pressCount(this)
            if (pressTimes.size >= need) {
                pressTimes.clear()
                if (now - lastTrigger > 15_000L) {
                    lastTrigger = now
                    LogBus.warn("音量键连按 $need 次，触发紧急救机")
                    EmergencyNotifier.trigger(this, "音量键")
                }
            }
        }
        // 返回 false 不拦截按键，音量仍然正常工作
        return false
    }

    override fun onDestroy() {
        EmergencyNotifier.setAccessibilityConnected(this, false)
        super.onDestroy()
    }

    private var lastTrigger = 0L
}
