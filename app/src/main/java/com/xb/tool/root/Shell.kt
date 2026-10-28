package com.xb.tool.root

import java.util.concurrent.TimeUnit

object Shell {
    data class Result(
        val code: Int,
        val out: String,
        val err: String,
        val timedOut: Boolean = false
    ) {
        val success: Boolean get() = code == 0 && !timedOut
        val text: String get() = listOf(out, err).filter { it.isNotBlank() }.joinToString("\n")
    }

    fun exec(command: String, timeoutMs: Long = 10_000L, asRoot: Boolean = false): Result {
        val builder = if (asRoot) {
            ProcessBuilder("su", "-c", command)
        } else {
            ProcessBuilder("sh", "-c", command)
        }
        builder.redirectErrorStream(false)
        return try {
            val process = builder.start()
            val output = StringBuilder()
            val error = StringBuilder()
            val outThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { output.append(it.readText()) }
                }
            }.apply { isDaemon = true; name = "xb-shell-out" }
            val errThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().use { error.append(it.readText()) }
                }
            }.apply { isDaemon = true; name = "xb-shell-err" }
            outThread.start()
            errThread.start()
            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(400, TimeUnit.MILLISECONDS)
                outThread.join(300)
                errThread.join(300)
                return Result(-1, output.toString().trim(), error.toString().trim(), timedOut = true)
            }
            outThread.join(800)
            errThread.join(800)
            Result(process.exitValue(), output.toString().trim(), error.toString().trim())
        } catch (t: Throwable) {
            Result(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    fun rootAvailable(): Boolean = exec("id", 6_000L, asRoot = true).let {
        !it.timedOut && it.out.contains("uid=0")
    }
}
