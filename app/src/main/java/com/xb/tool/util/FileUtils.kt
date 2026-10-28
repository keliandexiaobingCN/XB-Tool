package com.xb.tool.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble() / 1024.0
        var index = 0
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return if (value >= 100) {
            String.format(Locale.US, "%.0f %s", value, units[index])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"
    }

    fun formatTime(millis: Long): String {
        if (millis <= 0) return "—"
        return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    fun formatTimeFull(millis: Long): String {
        if (millis <= 0) return "—"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
    }

    fun openFile(context: Context, file: File, mime: String? = null) {
        if (!file.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime ?: mimeOf(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun installApk(context: Context, file: File) {
        if (!file.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            context.startActivity(intent)
        }
    }

    fun shareFile(context: Context, file: File, mime: String? = null) {
        if (!file.exists()) return
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime ?: mimeOf(file)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "分享文件"))
        }
    }

    fun copyUriToFile(context: Context, uri: Uri, destination: File): Boolean {
        return runCatching {
            destination.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            }
            destination.exists() && destination.length() > 0
        }.getOrDefault(false)
    }

    fun dirSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var total = 0L
        val children = runCatching { file.listFiles() }.getOrNull() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) dirSize(child) else runCatching { child.length() }.getOrDefault(0L)
        }
        return total
    }

    fun deleteContents(file: File): Long {
        if (!file.exists()) return 0L
        var freed = 0L
        val children = runCatching { file.listFiles() }.getOrNull()
        if (children == null) {
            return runCatching { file.length() }.getOrDefault(0L)
        }
        for (child in children) {
            freed += if (child.isDirectory) {
                val size = dirSize(child)
                val ok = runCatching { child.deleteRecursively() }.getOrDefault(false)
                if (ok) size else 0L
            } else {
                val size = runCatching { child.length() }.getOrDefault(0L)
                val ok = runCatching { child.delete() }.getOrDefault(false)
                if (ok) size else 0L
            }
        }
        return freed
    }

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), "_").take(120).ifBlank { "file" }
    }

    fun externalDownloadDir(context: Context): File {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        val target = File(dir, "XB工具")
        target.mkdirs()
        return target
    }

    fun externalAudioDir(context: Context): File {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            ?: File(context.filesDir, "music")
        val target = File(dir, "XB工具")
        target.mkdirs()
        return target
    }

    fun externalPdfDir(context: Context): File {
        val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, "documents")
        val target = File(dir, "XB工具")
        target.mkdirs()
        return target
    }
}
