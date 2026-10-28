package com.xb.tool.system

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.xb.tool.privilege.PrivilegeExec
import java.io.File

/**
 * 系统资源采集。
 *
 * 数据来源分三层，按可靠性从高到低：
 *  1. 标准 Android API（ActivityManager / MemoryInfo / Build）—— 无需权限，任何设备都能读；
 *  2. sysfs（/sys/devices/system/cpu、/sys/class/thermal）—— 部分字段普通应用可读，
 *     读不到就返回 null，绝不编造数值；
 *  3. 特权通道（Root / Shizuku / 无线调试）—— 读 GPU 频率、功率、更细的温度传感器。
 *
 * 设计原则：每个字段都可为 null。界面上 null 显示「不可用」，
 * 因为不同 SoC 的 sysfs 布局差异极大，硬凑数字比显示「不可用」更误导用户。
 */
object SystemResources {

    data class CpuCore(
        val index: Int,
        val curFreqMhz: Int?,
        val maxFreqMhz: Int?,
        val minFreqMhz: Int?
    )

    data class Snapshot(
        val cpuModel: String,
        val cpuCores: Int,
        val cpuAbi: String,
        val cores: List<CpuCore>,
        val cpuUsagePercent: Float?,
        val cpuTempC: Float?,
        val gpuModel: String,
        val gpuFreqMhz: Int?,
        val gpuMaxFreqMhz: Int?,
        val gpuLoadPercent: Float?,
        val gpuTempC: Float?,
        val batteryTempC: Float?,
        val batteryPowerW: Float?,
        val memTotalMb: Long,
        val memUsedMb: Long,
        val memAvailableMb: Long,
        val memPercent: Float,
        val swapTotalMb: Long,
        val swapUsedMb: Long,
        val uptimeText: String,
        val privileged: Boolean,
        val channel: String
    )

    fun sample(context: Context): Snapshot {
        val privileged = PrivilegeExec.hasPrivilege()
        val channel = PrivilegeExec.channel()

        val cores = readCpuCores()
        val mem = readMemory(context)

        return Snapshot(
            cpuModel = readCpuModel(),
            cpuCores = Runtime.getRuntime().availableProcessors(),
            cpuAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "未知",
            cores = cores,
            cpuUsagePercent = readCpuUsage(),
            cpuTempC = readThermal("cpu") ?: readThermalZoneByType(listOf("cpu", "soc", "ap", "tsens_tz_sensor0")),
            gpuModel = readGpuModel(),
            gpuFreqMhz = readGpuFreqMhz(privileged),
            gpuMaxFreqMhz = readGpuMaxFreqMhz(),
            gpuLoadPercent = readGpuLoad(privileged),
            gpuTempC = readThermal("gpu"),
            batteryTempC = readBatteryTemp(context),
            batteryPowerW = readBatteryPower(context),
            memTotalMb = mem[0],
            memUsedMb = mem[1],
            memAvailableMb = mem[2],
            memPercent = if (mem[0] > 0) mem[1].toFloat() / mem[0] * 100f else 0f,
            swapTotalMb = mem[3],
            swapUsedMb = mem[4],
            uptimeText = readUptime(),
            privileged = privileged,
            channel = channel
        )
    }

    // ------------------------------------------------------------ CPU

    private fun readCpuModel(): String {
        val hardware = readProp("ro.hardware.chipname")
            ?: readProp("ro.soc.model")
            ?: readProp("ro.board.platform")
        val fromCpuinfo = runCatching {
            File("/proc/cpuinfo").readLines()
                .firstOrNull { it.startsWith("Hardware", true) || it.startsWith("model name", true) }
                ?.substringAfter(':')?.trim()
        }.getOrNull()
        return listOfNotNull(hardware, fromCpuinfo).firstOrNull { it.isNotBlank() }
            ?: Build.HARDWARE.ifBlank { "未知" }
    }

    private fun readProp(name: String): String? = runCatching {
        val process = ProcessBuilder("getprop", name).start()
        val text = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        text.ifBlank { null }
    }.getOrNull()

    private fun readCpuCores(): List<CpuCore> {
        val count = Runtime.getRuntime().availableProcessors()
        return (0 until count).map { i ->
            CpuCore(
                index = i,
                curFreqMhz = readIntFile("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")?.div(1000),
                maxFreqMhz = readIntFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")?.div(1000),
                minFreqMhz = readIntFile("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")?.div(1000)
            )
        }
    }

    /**
     * CPU 总体占用率：采样两次 /proc/stat，间隔 300ms 算差值。
     * 这是 Linux 上最标准的算法，和 top 的结果一致。
     */
    private fun readCpuUsage(): Float? {
        fun readStat(): LongArray? = runCatching {
            val line = File("/proc/stat").readLines().firstOrNull { it.startsWith("cpu ") } ?: return null
            line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }.toLongArray()
        }.getOrNull()

        val first = readStat() ?: return null
        Thread.sleep(300)
        val second = readStat() ?: return null
        if (first.size < 4 || second.size < 4) return null
        val idleFirst = first[3] + (first.getOrNull(4) ?: 0)
        val idleSecond = second[3] + (second.getOrNull(4) ?: 0)
        val totalFirst = first.sum()
        val totalSecond = second.sum()
        val totalDiff = totalSecond - totalFirst
        if (totalDiff <= 0) return null
        val idleDiff = idleSecond - idleFirst
        return ((totalDiff - idleDiff).toFloat() / totalDiff * 100f).coerceIn(0f, 100f)
    }

    private fun readUptime(): String = runCatching {
        val seconds = File("/proc/uptime").readText().trim().substringBefore(' ').toDoubleOrNull() ?: return "未知"
        val total = seconds.toLong()
        val d = total / 86_400
        val h = (total % 86_400) / 3_600
        val m = (total % 3_600) / 60
        if (d > 0) "${d}天 ${h}小时 ${m}分" else "${h}小时 ${m}分"
    }.getOrDefault("未知")

    // ------------------------------------------------------------ GPU

    private fun readGpuModel(): String {
        val candidates = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_model",
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/misc/mali0/device/gpuinfo"
        )
        candidates.forEach { path ->
            val text = runCatching { File(path).readText().trim() }.getOrNull()
            if (!text.isNullOrBlank() && text.length < 64) return text
        }
        return readProp("ro.hardware.egl") ?: Build.HARDWARE.ifBlank { "未知" }
    }

    private fun readGpuFreqMhz(privileged: Boolean): Int? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/misc/mali0/device/cur_freq",
            "/sys/kernel/gpu/gpu_clock"
        )
        paths.forEach { path ->
            readIntFile(path)?.let { raw ->
                // 高通 gpuclk 单位是 Hz，devfreq 也是 Hz；mali 部分是 KHz
                return when {
                    raw > 10_000_000 -> raw / 1_000_000
                    raw > 10_000 -> raw / 1_000
                    else -> raw
                }
            }
        }
        if (!privileged) return null
        return null
    }

    private fun readGpuMaxFreqMhz(): Int? {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/max_gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/misc/mali0/device/max_freq"
        )
        paths.forEach { path ->
            readIntFile(path)?.let { raw ->
                return when {
                    raw > 10_000_000 -> raw / 1_000_000
                    raw > 10_000 -> raw / 1_000
                    else -> raw
                }
            }
        }
        return null
    }

    private fun readGpuLoad(privileged: Boolean): Float? {
        // 高通：gpu_busy_percentage 直接是百分比
        runCatching {
            val text = File("/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage").readText().trim()
            text.filter { it.isDigit() }.toFloatOrNull()
        }.getOrNull()?.let { return it.coerceIn(0f, 100f) }
        // Mali：部分机型有 utilization
        runCatching {
            File("/sys/class/misc/mali0/device/utilisation").readText().trim().toFloatOrNull()
        }.getOrNull()?.let { return it.coerceIn(0f, 100f) }
        return null
    }

    // ------------------------------------------------------------ 温度

    private fun readThermal(kind: String): Float? = readThermalZoneByType(listOf(kind))

    /** 遍历 /sys/class/thermal/thermal_zone*，按 type 关键字匹配。 */
    private fun readThermalZoneByType(keywords: List<String>): Float? {
        val root = File("/sys/class/thermal")
        val zones = root.listFiles { f -> f.name.startsWith("thermal_zone") } ?: return null
        zones.forEach { zone ->
            val type = runCatching { File(zone, "type").readText().trim().lowercase() }.getOrNull() ?: return@forEach
            if (keywords.none { type.contains(it) }) return@forEach
            val raw = readIntFile(File(zone, "temp").path) ?: return@forEach
            val celsius = if (raw > 1000) raw / 1000f else raw.toFloat()
            if (celsius in 1f..150f) return celsius
        }
        return null
    }

    // ------------------------------------------------------------ 电池

    private fun readBatteryTemp(context: Context): Float? = runCatching {
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: return null
        if (tenths == Int.MIN_VALUE) null else tenths / 10f
    }.getOrNull()

    /**
     * 电池功率：从 BatteryManager 的电流与电压估算。
     * 注意厂商上报的电流符号不统一（有的放电为正，有的为负），这里取绝对值。
     */
    private fun readBatteryPower(context: Context): Float? = runCatching {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            ?: return null
        val microAmps = bm.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val voltageMv = context.registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )?.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        if (microAmps == Long.MIN_VALUE || voltageMv <= 0) return null
        val amps = kotlin.math.abs(microAmps) / 1_000_000f
        amps * (voltageMv / 1000f)
    }.getOrNull()

    // ------------------------------------------------------------ 内存

    /** 返回 [总内存MB, 已用MB, 可用MB, swap总量MB, swap已用MB]。 */
    private fun readMemory(context: Context): LongArray {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val info = ActivityManager.MemoryInfo()
        runCatching { am?.getMemoryInfo(info) }
        val totalMb = info.totalMem / (1024 * 1024)
        val availMb = info.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)

        var swapTotal = 0L
        var swapUsed = 0L
        runCatching {
            File("/proc/meminfo").readLines().forEach { line ->
                when {
                    line.startsWith("SwapTotal:") -> swapTotal = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    line.startsWith("SwapFree:") -> {
                        val free = line.filter { it.isDigit() }.toLongOrNull() ?: 0L
                        swapUsed = (swapTotal - free).coerceAtLeast(0)
                    }
                }
            }
        }
        return longArrayOf(totalMb, usedMb, availMb, swapTotal / 1024, swapUsed / 1024)
    }

    private fun readIntFile(path: String): Int? = runCatching {
        File(path).readText().trim().toLongOrNull()?.toInt()
    }.getOrNull()
}
