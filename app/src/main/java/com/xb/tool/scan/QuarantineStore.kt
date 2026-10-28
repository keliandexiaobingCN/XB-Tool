package com.xb.tool.scan

import android.content.Context
import com.xb.tool.core.Threat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class QuarantineEntry(
    val id: String,
    val name: String,
    val threatName: String,
    val description: String,
    val originalPath: String,
    val storedPath: String,
    val time: Long,
    val moved: Boolean
)

/**
 * A small, local quarantine. Files that cannot be read without Root/Shizuku are
 * still recorded so the user can see what was intercepted and act manually.
 */
object QuarantineStore {
    private const val FILE = "quarantine.json"

    fun entries(context: Context): List<QuarantineEntry> {
        val file = File(context.filesDir, FILE)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                QuarantineEntry(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    threatName = item.optString("threat"),
                    description = item.optString("description"),
                    originalPath = item.optString("original"),
                    storedPath = item.optString("stored"),
                    time = item.optLong("time"),
                    moved = item.optBoolean("moved")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun record(
        context: Context,
        threat: Threat,
        moveFile: Boolean
    ): QuarantineEntry {
        val name = File(threat.path).name.ifBlank { threat.name }
        var storedPath = ""
        var moved = false
        if (moveFile) {
            val source = File(threat.path)
            if (source.isFile && source.canRead()) {
                val dir = File(context.filesDir, "quarantine").apply { mkdirs() }
                val target = File(dir, "${UUID.randomUUID()}.bin")
                moved = runCatching {
                    source.copyTo(target, overwrite = true)
                    if (target.isFile && target.length() > 0) {
                        source.delete()
                        true
                    } else {
                        target.delete()
                        false
                    }
                }.getOrDefault(false)
                if (moved) storedPath = target.absolutePath
            }
        }
        val entry = QuarantineEntry(
            id = UUID.randomUUID().toString(),
            name = name,
            threatName = threat.name,
            description = threat.description,
            originalPath = threat.path,
            storedPath = storedPath,
            time = System.currentTimeMillis(),
            moved = moved
        )
        val current = entries(context).toMutableList()
        current.removeAll { it.originalPath == entry.originalPath }
        current.add(0, entry)
        write(context, current)
        return entry
    }

    fun recordMoved(context: Context, threat: Threat, storedPath: String): QuarantineEntry {
        val entry = QuarantineEntry(
            id = UUID.randomUUID().toString(),
            name = File(storedPath).name,
            threatName = threat.name,
            description = threat.description,
            originalPath = threat.path,
            storedPath = storedPath,
            time = System.currentTimeMillis(),
            moved = true
        )
        val current = entries(context).toMutableList()
        current.removeAll { it.originalPath == entry.originalPath }
        current.add(0, entry)
        write(context, current)
        return entry
    }

    fun restore(context: Context, entry: QuarantineEntry): Boolean {
        if (!entry.moved || entry.storedPath.isBlank()) return false
        val stored = File(entry.storedPath)
        if (!stored.isFile) return false
        val target = File(entry.originalPath)
        val ok = runCatching {
            target.parentFile?.mkdirs()
            stored.copyTo(target, overwrite = true)
            stored.delete()
            true
        }.getOrDefault(false)
        if (ok) removeRecord(context, entry.id)
        return ok
    }

    fun delete(context: Context, entry: QuarantineEntry): Boolean {
        if (entry.storedPath.isNotBlank()) {
            runCatching { File(entry.storedPath).delete() }
        }
        removeRecord(context, entry.id)
        return true
    }

    fun clear(context: Context) {
        entries(context).forEach { entry ->
            if (entry.storedPath.isNotBlank()) runCatching { File(entry.storedPath).delete() }
        }
        File(context.filesDir, FILE).delete()
        File(context.filesDir, "quarantine").deleteRecursively()
    }

    private fun removeRecord(context: Context, id: String) {
        write(context, entries(context).filterNot { it.id == id })
    }

    private fun write(context: Context, list: List<QuarantineEntry>) {
        val array = JSONArray()
        list.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("name", entry.name)
                    put("threat", entry.threatName)
                    put("description", entry.description)
                    put("original", entry.originalPath)
                    put("stored", entry.storedPath)
                    put("time", entry.time)
                    put("moved", entry.moved)
                }
            )
        }
        File(context.filesDir, FILE).writeText(array.toString())
    }
}
