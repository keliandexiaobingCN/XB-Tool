package com.xb.tool.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.xb.tool.core.LogBus
import java.io.File
import java.io.FileOutputStream
import kotlin.math.PI
import kotlin.math.sin

/** Debug-only end-to-end check for MP3 / FLAC / WAV encoders. */
object AudioSelfTest {
    fun run(context: Context) {
        val report = StringBuilder()
        try {
            val cache = File(context.cacheDir, "audio_selftest").apply { mkdirs() }
            val wav = File(cache, "selftest_input.wav")
            writeSineWav(wav, seconds = 2, sampleRate = 44100, channels = 2)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                wav
            )
            listOf(
                AudioConverter.OutputFormat.MP3,
                AudioConverter.OutputFormat.FLAC,
                AudioConverter.OutputFormat.WAV,
                AudioConverter.OutputFormat.AAC
            ).forEach { format ->
                try {
                    val output = AudioConverter.convert(
                        context = context,
                        uri = uri,
                        displayName = "xb_selftest",
                        target = format,
                        bitrateKbps = 192,
                        onProgress = { _, _ -> },
                        isCancelled = { false }
                    )
                    val header = output.inputStream().use { input ->
                        val bytes = ByteArray(16)
                        val read = input.read(bytes)
                        bytes.take(read.coerceAtLeast(0)).joinToString("") { "%02x".format(it) }
                    }
                    report.appendLine("${format.name}: OK ${output.name} ${output.length()} bytes header=$header")
                    LogBus.ok("音频自检 ${format.name} 成功：${output.length()} bytes")
                } catch (t: Throwable) {
                    report.appendLine("${format.name}: FAIL ${t.message}")
                    LogBus.error("音频自检 ${format.name} 失败：${t.message}")
                }
            }
            wav.delete()
        } catch (t: Throwable) {
            report.appendLine("SETUP FAIL ${t.message}")
            LogBus.error("音频自检初始化失败：${t.message}")
        }
        runCatching {
            File(context.filesDir, "audio_selftest.txt").writeText(report.toString())
        }
    }

    private fun writeSineWav(file: File, seconds: Int, sampleRate: Int, channels: Int) {
        val frames = seconds * sampleRate
        val dataSize = frames * channels * 2
        FileOutputStream(file).use { out ->
            val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * 2)
            header.putShort((channels * 2).toShort())
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataSize)
            out.write(header.array())
            val buffer = java.nio.ByteBuffer.allocate(frames * channels * 2)
                .order(java.nio.ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until frames) {
                val value = (sin(2.0 * PI * 440.0 * i / sampleRate) * 12000).toInt().toShort()
                repeat(channels) { buffer.putShort(value) }
            }
            out.write(buffer.array())
        }
    }
}
