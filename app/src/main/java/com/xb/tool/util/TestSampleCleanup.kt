package com.xb.tool.util

import android.content.Context
import android.os.Environment
import java.io.File

/** Removes EICAR test files created by older versions, which security scanners may flag. */
object TestSampleCleanup {
    private val names = setOf(
        "eicar.com",
        "xb_check.txt",
        "eicar_realtime.txt",
        "eicar_realtime2.txt",
        "sample_test.txt"
    )

    fun cleanup(context: Context) {
        val dirs = listOfNotNull(
            runCatching { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }.getOrNull(),
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        )
        dirs.forEach { dir ->
            names.forEach { name ->
                runCatching { File(dir, name).delete() }
            }
            runCatching {
                dir.listFiles()?.filter { it.name.startsWith("xb_selftest_") }?.forEach { it.delete() }
            }
        }
    }
}
