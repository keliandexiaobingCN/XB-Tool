package com.xb.tool.root

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.xb.tool.shizuku.IUserService
import com.xb.tool.shizuku.ShizukuUserService
import rikka.shizuku.Shizuku

object ShizukuUtils {
    private var service: IUserService? = null
    private var connection: ServiceConnection? = null
    private var lastBindAttempt = 0L

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isGranted(): Boolean = runCatching {
        isBinderAlive() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun uid(): Int = runCatching { Shizuku.getUid() }.getOrDefault(-1)

    fun version(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(0)

    fun requestPermission(requestCode: Int): Boolean {
        if (!isBinderAlive()) return false
        return runCatching {
            Shizuku.requestPermission(requestCode)
            true
        }.getOrDefault(false)
    }

    fun shouldShowRationale(): Boolean = runCatching {
        isBinderAlive() && Shizuku.shouldShowRequestPermissionRationale()
    }.getOrDefault(false)

    /**
     * Binds the Shizuku UserService once. Callback is delivered on the caller's
     * thread as decided by the binder (usually main thread).
     */
    fun bind(context: Context, callback: (Boolean) -> Unit) {
        if (!isGranted()) {
            callback(false)
            return
        }
        service?.let {
            callback(true)
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBindAttempt < 1_500L) {
            callback(false)
            return
        }
        lastBindAttempt = now
        runCatching {
            val args = Shizuku.UserServiceArgs(
                ComponentName(context.packageName, ShizukuUserService::class.java.name)
            )
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(1)
            var callbackUsed = false
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    service = runCatching { IUserService.Stub.asInterface(binder) }.getOrNull()
                    if (!callbackUsed) {
                        callbackUsed = true
                        callback(service != null)
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    service = null
                }
            }
            connection = conn
            Shizuku.bindUserService(args, conn)
        }.onFailure {
            callback(false)
        }
    }

    fun isServiceReady(): Boolean = service != null

    fun exec(command: String): Shell.Result {
        val binder = service ?: return Shell.Result(-1, "", "Shizuku 服务尚未连接")
        return try {
            val out = binder.exec(command)
            Shell.Result(0, out?.trim().orEmpty(), "")
        } catch (t: Throwable) {
            Shell.Result(-1, "", t.message ?: "Shizuku 调用失败")
        }
    }

    fun reset() {
        runCatching {
            service = null
        }
    }
}
