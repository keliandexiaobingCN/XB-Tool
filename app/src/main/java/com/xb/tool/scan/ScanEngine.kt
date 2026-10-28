package com.xb.tool.scan

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import com.xb.tool.core.Prefs
import com.xb.tool.core.Severity
import com.xb.tool.core.Threat
import com.xb.tool.root.Shell
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Locale

enum class ScanMode(val title: String, val subtitle: String, val needsPrivilege: Boolean) {
    NORMAL("普通杀毒", "扫描常用目录与已安装应用", false),
    DEEP("强力杀毒", "深度扫描系统分区与受保护目录", true)
}

class ScanEngine(
    private val context: Context,
    private val database: VirusDatabase
) {
    private val prefs = Prefs(context)
    private val maxNormalFiles = 4_500
    private val maxDeepFiles = 9_000
    private val maxFileSize = 96L * 1024L * 1024L
    private val headerBytes = 64 * 1024

    private val scanExtensions = setOf(
        "apk", "dex", "jar", "zip", "sh", "bin", "elf", "so", "txt", "html", "htm",
        "js", "vbs", "bat", "cmd", "py", "php", "pdf", "doc", "docx", "xls", "xlsx",
        "ppt", "pptx", "exe", "com", "eicar", "img", "iso", "xml", "json"
    )

    private val skipDirs = setOf(
        ".thumbnails", ".cache", "cache", "code_cache", "no_backup", ".trash",
        "obb", ".git", "node_modules", "__pycache__"
    )

    fun scan(
        mode: ScanMode,
        onProgress: (scanned: Int, total: Int, current: String, found: Int) -> Unit,
        onThreat: (Threat) -> Unit = {},
        isCancelled: () -> Boolean,
        privilegedExec: ((String) -> Shell.Result)? = null
    ): List<Threat> {
        val targets = collectTargets(mode)
        val total = targets.size
        val found = linkedMapOf<String, Threat>()
        var scanned = 0
        for (file in targets) {
            if (isCancelled()) break
            scanned++
            if (scanned == 1 || total <= 300 || scanned % 10 == 0 || scanned == total) {
                onProgress(scanned, total, file.absolutePath, found.size)
            }
            val packageName = packageNameForPath(file.absolutePath)
            scanFile(file, packageName, if (mode == ScanMode.DEEP) "深度扫描" else "文件扫描")
                ?.let { threat ->
                    if (!found.containsKey(threat.path)) {
                        found[threat.path] = threat
                        onThreat(threat)
                    }
                }
        }
        if (mode == ScanMode.DEEP && !isCancelled() && privilegedExec != null) {
            onProgress(scanned, total, "正在读取受保护目录 …", found.size)
            scanPrivilegedPaths(privilegedExec).forEach { threat ->
                if (!found.containsKey(threat.path)) {
                    found[threat.path] = threat
                    onThreat(threat)
                }
                scanned++
            }
            onProgress(scanned, total, "扫描完成", found.size)
        }
        return found.values.toList()
    }

    fun scanFile(
        file: File,
        packageName: String? = null,
        source: String = "文件扫描"
    ): Threat? {
        if (isCancelledFile(file)) return null
        val name = file.name
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val recognizable = ext in scanExtensions || ext.isEmpty()
        val systemPath = file.absolutePath.startsWith("/system/") ||
            file.absolutePath.startsWith("/apex/") ||
            file.absolutePath.startsWith("/product/") ||
            file.absolutePath.startsWith("/vendor/")

        if (recognizable && !systemPath) {
            database.names.firstOrNull { it.regex.containsMatchIn(name) }?.let { rule ->
                return Threat(
                    id = file.absolutePath,
                    name = rule.name,
                    path = file.absolutePath,
                    severity = rule.severity,
                    description = rule.description,
                    packageName = packageName,
                    source = "文件名启发"
                )
            }
        }

        if (file.length() <= 0L || file.length() > maxFileSize) return null

        val header = readHeader(file)
        if (header.isNotEmpty()) {
            database.patterns.firstOrNull { header.contains(it.text) }?.let { entry ->
                val hash = sha256(file)
                return Threat(
                    id = file.absolutePath,
                    name = entry.name,
                    path = file.absolutePath,
                    sha256 = hash,
                    severity = entry.severity,
                    description = entry.description,
                    packageName = packageName,
                    source = source
                )
            }
        }

        if (ext !in scanExtensions) return null
        val hash = sha256(file) ?: return null
        database.hashes[hash]?.let { entry ->
            return Threat(
                id = file.absolutePath,
                name = entry.name,
                path = file.absolutePath,
                sha256 = hash,
                severity = entry.severity,
                description = entry.description,
                packageName = packageName,
                source = source
            )
        }
        return null
    }

    /**
     * 特权扫描：读取受保护目录（/data/app、/data/adb/modules、/data/local/tmp）里的文件哈希。
     * 严格模式下实时防护也会周期性调用它，所以对外公开。
     */
    fun scanPrivilegedPaths(exec: (String) -> Shell.Result): List<Threat> {
        val command = """
            find /data/local/tmp /data/adb/modules /data/adb/ksu /data/app \
              -type f -size -64M 2>/dev/null | head -n 1200 | xargs sha256sum 2>/dev/null
        """.trimIndent()
        val result = runCatching { exec(command) }.getOrNull() ?: return emptyList()
        if (result.out.isBlank()) return emptyList()
        val threats = mutableListOf<Threat>()
        val hashLine = Regex("^([0-9a-fA-F]{64})\\s+(.+)$")
        result.out.lineSequence().forEach { line ->
            val match = hashLine.find(line.trim()) ?: return@forEach
            val hash = match.groupValues[1].lowercase(Locale.ROOT)
            val path = match.groupValues[2].trim()
            database.hashes[hash]?.let { entry ->
                threats += Threat(
                    id = path,
                    name = entry.name,
                    path = path,
                    sha256 = hash,
                    severity = entry.severity,
                    description = entry.description,
                    source = "深度扫描 · 特权"
                )
            }
            val fileName = path.substringAfterLast('/')
            database.names.firstOrNull { it.regex.containsMatchIn(fileName) }?.let { rule ->
                threats += Threat(
                    id = path,
                    name = rule.name,
                    path = path,
                    sha256 = hash,
                    severity = rule.severity,
                    description = rule.description,
                    source = "深度扫描 · 特权"
                )
            }
        }
        return threats
    }

    private fun collectTargets(mode: ScanMode): List<File> {
        val paths = LinkedHashSet<String>()
        val whitelist = prefs.whitelist()
        val pm = context.packageManager

        runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA).forEach { app ->
                if (app.packageName == context.packageName || app.packageName in whitelist) return@forEach
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                if (mode == ScanMode.NORMAL && isSystem && !isUpdatedSystem) return@forEach
                app.sourceDir?.let { path ->
                    val file = File(path)
                    if (file.isFile && file.length() > 0) paths += file.absolutePath
                }
                app.publicSourceDir?.takeIf { it != app.sourceDir }?.let { path ->
                    val file = File(path)
                    if (file.isFile && file.length() > 0) paths += file.absolutePath
                }
            }
        }

        val storage = Environment.getExternalStorageDirectory()
        val commonDirs = listOf(
            "Download", "DCIM", "Pictures", "Movies", "Documents", "Music",
            "Alarms", "Ringtones", "Notifications", "Podcasts", "Recordings"
        )
        commonDirs.forEach { dirName ->
            walk(File(storage, dirName), mode, paths)
        }
        context.getExternalFilesDir(null)?.let { appDir ->
            walk(appDir, mode, paths)
        }

        if (mode == ScanMode.DEEP) {
            listOf(
                "/system/app", "/system/priv-app", "/system/etc", "/system/framework",
                "/vendor/app", "/product/app", "/data/local/tmp", "/data/adb/modules",
                "/data/app"
            ).forEach { path ->
                walk(File(path), mode, paths)
            }
        }

        return paths.map { File(it) }.filter { it.isFile }
    }

    private fun walk(root: File, mode: ScanMode, paths: MutableSet<String>) {
        if (!runCatching { root.exists() }.getOrDefault(false)) return
        val limit = if (mode == ScanMode.DEEP) maxDeepFiles else maxNormalFiles
        val stack = ArrayDeque<Pair<File, Int>>()
        stack.addLast(root to 0)
        while (stack.isNotEmpty() && paths.size < limit) {
            val (dir, depth) = stack.removeLast()
            if (depth > 7) continue
            val children = runCatching { dir.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                if (paths.size >= limit) break
                if (child.isDirectory) {
                    if (child.name.lowercase(Locale.ROOT) in skipDirs) continue
                    val isLink = runCatching { child.canonicalPath != child.absolutePath }.getOrDefault(false)
                    if (isLink) continue
                    stack.addLast(child to depth + 1)
                } else if (child.isFile && child.length() > 0) {
                    paths += child.absolutePath
                }
            }
        }
    }

    private fun packageNameForPath(path: String): String? {
        return installedAppPaths[path]
    }

    private val installedAppPaths: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        runCatching {
            context.packageManager.getInstalledApplications(0).forEach { app ->
                app.sourceDir?.let { map[it] = app.packageName }
                app.publicSourceDir?.let { map[it] = app.packageName }
            }
        }
        map
    }

    private fun isCancelledFile(file: File): Boolean {
        val name = file.name
        if (name.startsWith(".")) return true
        if (name.endsWith(".tmp") || name.endsWith(".log")) return true
        return !runCatching { file.canRead() }.getOrDefault(false)
    }

    private fun readHeader(file: File): String {
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = minOf(raf.length(), headerBytes.toLong()).toInt()
                val bytes = ByteArray(length)
                raf.readFully(bytes)
                String(bytes, Charsets.ISO_8859_1)
            }
        }.getOrDefault("")
    }

    private fun sha256(file: File): String? {
        return runCatching {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }
}
