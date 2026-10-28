package com.xb.tool.util

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.github.axet.lamejni.Lame
import net.sourceforge.javaflacencoder.EncodingConfiguration
import net.sourceforge.javaflacencoder.FLACEncoder
import net.sourceforge.javaflacencoder.FLACFileOutputStream
import net.sourceforge.javaflacencoder.StreamConfiguration
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

object AudioConverter {
    enum class OutputFormat(val label: String, val extension: String, val mime: String) {
        MP3("MP3", "mp3", "audio/mpeg"),
        WAV("WAV 无损", "wav", "audio/wav"),
        AAC("AAC / M4A", "m4a", "audio/mp4"),
        FLAC("FLAC 无损", "flac", "audio/flac")
    }

    data class MediaInfo(
        val durationMs: Long,
        val sampleRate: Int,
        val channels: Int,
        val mime: String
    )

    fun inspect(context: Context, uri: Uri): MediaInfo? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                val rate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.toIntOrNull() ?: 0
                val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE).orEmpty()
                MediaInfo(duration, rate, 2, mime)
            } finally {
                retriever.release()
            }
        }.getOrNull()
    }

    fun convert(
        context: Context,
        uri: Uri,
        displayName: String,
        target: OutputFormat,
        bitrateKbps: Int,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ): File {
        val cache = File(context.cacheDir, "audio_tmp").apply { mkdirs() }
        val input = File(cache, "input_${System.currentTimeMillis()}")
        onProgress(0.01f, "正在读取音频…")
        if (!FileUtils.copyUriToFile(context, uri, input)) {
            throw IllegalStateException("无法读取所选音频文件")
        }
        try {
            onProgress(0.04f, "正在解码音频流…")
            val pcm = decodeToPcm(input, cache, onProgress, isCancelled)
            val base = FileUtils.sanitizeFileName(displayName.substringBeforeLast('.'))
            val outDir = FileUtils.externalAudioDir(context)
            var output = File(outDir, "$base.${target.extension}")
            var counter = 1
            while (output.exists()) {
                output = File(outDir, "${base}_$counter.${target.extension}")
                counter++
            }
            when (target) {
                OutputFormat.MP3 -> {
                    onProgress(0.88f, "正在编码 MP3（LAME）…")
                    encodeMp3(pcm, output, bitrateKbps, onProgress, isCancelled)
                }
                OutputFormat.WAV -> {
                    onProgress(0.88f, "正在封装 WAV…")
                    writeWav(pcm, output)
                }
                OutputFormat.AAC -> {
                    onProgress(0.88f, "正在编码 AAC…")
                    encodeAac(pcm, output, bitrateKbps, onProgress, isCancelled)
                }
                OutputFormat.FLAC -> {
                    onProgress(0.88f, "正在编码 FLAC…")
                    encodeFlac(pcm, output, onProgress, isCancelled)
                }
            }
            onProgress(1f, "转换完成")
            return output
        } finally {
            runCatching { input.delete() }
        }
    }

    private data class Pcm(val file: File, val sampleRate: Int, val channels: Int)

    private fun decodeToPcm(
        input: File,
        cache: File,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ): Pcm {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val pcmFile = File(cache, "pcm_${System.currentTimeMillis()}.raw")
        return try {
            extractor.setDataSource(input.absolutePath)
            var trackIndex = -1
            var mime = ""
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (trackMime.startsWith("audio/")) {
                    trackIndex = i
                    mime = trackMime
                    break
                }
            }
            if (trackIndex < 0) throw IllegalStateException("文件中没有可用的音频轨道")
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            var outRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var outChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else 1L

            runCatching {
                inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            }

            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            FileOutputStream(pcmFile).use { out ->
                while (!outputDone) {
                    if (isCancelled()) throw CancellationException("已取消")
                    if (!inputDone) {
                        val inIndex = decoder.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = decoder.getInputBuffer(inIndex)
                            if (buffer == null) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val sampleSize = extractor.readSampleData(buffer, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputDone = true
                                } else {
                                    decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }
                    val outIndex = decoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val format = decoder.outputFormat
                            outRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            outChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                        outIndex >= 0 -> {
                            val buffer = decoder.getOutputBuffer(outIndex)
                            if (buffer != null && info.size > 0) {
                                buffer.position(info.offset)
                                buffer.limit(info.offset + info.size)
                                val bytes = ByteArray(info.size)
                                buffer.get(bytes)
                                out.write(bytes)
                            }
                            decoder.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                            val progress = if (durationUs > 0) {
                                (info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 0.85f)
                            } else 0.4f
                            onProgress(progress, "解码中 ${(progress * 100).toInt()}%")
                        }
                    }
                }
            }
            Pcm(pcmFile, outRate, outChannels)
        } catch (t: Throwable) {
            runCatching { pcmFile.delete() }
            throw t
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun encodeMp3(
        pcm: Pcm,
        output: File,
        bitrateKbps: Int,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val channels = pcm.channels.coerceIn(1, 2)
        val sampleRate = pcm.sampleRate.coerceAtLeast(8000)
        val lame = Lame()
        var closed = false
        try {
            // axet/lamejni: open(channels, sampleRate, bitrateKbps, quality)
            lame.open(channels, sampleRate, bitrateKbps.coerceIn(32, 320), 5)
            output.parentFile?.mkdirs()
            FileOutputStream(output).use { out ->
                FileInputStream(pcm.file).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    val total = pcm.file.length().coerceAtLeast(1L)
                    var processed = 0L
                    while (true) {
                        if (isCancelled()) throw CancellationException("已取消")
                        val read = input.read(buffer)
                        if (read <= 0) break
                        val frames = read / (2 * channels)
                        if (frames <= 0) continue
                        val sampleCount = frames * channels
                        val samples = ShortArray(sampleCount)
                        var byteIndex = 0
                        for (i in 0 until sampleCount) {
                            val low = buffer[byteIndex].toInt() and 0xff
                            val high = buffer[byteIndex + 1].toInt()
                            samples[i] = ((high shl 8) or low).toShort()
                            byteIndex += 2
                        }
                        val encoded = lame.encode(samples, 0, sampleCount)
                        if (encoded != null && encoded.isNotEmpty()) out.write(encoded)
                        processed += read
                        val progress = (0.88f + (processed.toFloat() / total) * 0.11f).coerceIn(0.88f, 0.99f)
                        onProgress(progress, "MP3 编码 ${(processed * 100 / total)}%")
                    }
                }
                val tail = lame.close()
                closed = true
                if (tail != null && tail.isNotEmpty()) out.write(tail)
            }
        } finally {
            if (!closed) runCatching { lame.close() }
        }
    }

    private fun encodeFlac(
        pcm: Pcm,
        output: File,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val channels = pcm.channels.coerceIn(1, 8)
        val sampleRate = pcm.sampleRate.coerceAtLeast(8000)
        val encoder = FLACEncoder()
        val stream = FLACFileOutputStream(output)
        try {
            encoder.setStreamConfiguration(
                StreamConfiguration(channels, 4096, 4096, sampleRate, 16)
            )
            encoder.setEncodingConfiguration(EncodingConfiguration())
            encoder.setOutputStream(stream)
            encoder.openFLACStream()
            FileInputStream(pcm.file).use { input ->
                val buffer = ByteArray(32 * 1024)
                val total = pcm.file.length().coerceAtLeast(1L)
                var processed = 0L
                var unencodedFrames = 0
                while (true) {
                    if (isCancelled()) throw CancellationException("已取消")
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val frames = read / (2 * channels)
                    if (frames <= 0) continue
                    val sampleCount = frames * channels
                    val samples = IntArray(sampleCount)
                    var byteIndex = 0
                    for (i in 0 until sampleCount) {
                        val low = buffer[byteIndex].toInt() and 0xff
                        val high = buffer[byteIndex + 1].toInt()
                        samples[i] = (high shl 8) or low
                        byteIndex += 2
                    }
                    encoder.addSamples(samples, frames)
                    unencodedFrames += frames
                    unencodedFrames -= encoder.encodeSamples(unencodedFrames, false)
                    processed += read
                    val progress = (0.88f + (processed.toFloat() / total) * 0.11f).coerceIn(0.88f, 0.99f)
                    onProgress(progress, "FLAC 编码 ${(processed * 100 / total)}%")
                }
                if (unencodedFrames > 0) {
                    encoder.encodeSamples(unencodedFrames, true)
                } else {
                    encoder.encodeSamples(0, true)
                }
            }
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun writeWav(pcm: Pcm, output: File) {
        val dataSize = pcm.file.length().toInt()
        val channels = pcm.channels.coerceAtLeast(1)
        val sampleRate = pcm.sampleRate.coerceAtLeast(8000)
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + dataSize)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * 2).toShort())
        header.putShort(16)
        header.put("data".toByteArray())
        header.putInt(dataSize)
        output.parentFile?.mkdirs()
        FileOutputStream(output).use { out ->
            out.write(header.array())
            FileInputStream(pcm.file).use { input -> input.copyTo(out) }
        }
    }

    private fun encodeAac(
        pcm: Pcm,
        output: File,
        bitrateKbps: Int,
        onProgress: (Float, String) -> Unit,
        isCancelled: () -> Boolean
    ) {
        val sampleRate = pcm.sampleRate.coerceAtLeast(8000)
        val channels = pcm.channels.coerceIn(1, 2)
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateKbps * 1000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var trackIndex = -1
        var muxerStarted = false
        val info = MediaCodec.BufferInfo()
        val totalBytes = pcm.file.length().coerceAtLeast(1L)
        var processed = 0L
        try {
            FileInputStream(pcm.file).use { input ->
                var inputDone = false
                var outputDone = false
                var pts = 0L
                val frameSize = channels * 2
                while (!outputDone) {
                    if (isCancelled()) throw CancellationException("已取消")
                    if (!inputDone) {
                        val inIndex = encoder.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val buffer = encoder.getInputBuffer(inIndex)
                            buffer?.clear()
                            val chunk = ByteArray(16 * 1024)
                            val read = input.read(chunk)
                            if (read < 0) {
                                encoder.queueInputBuffer(inIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val aligned = read - (read % frameSize)
                                buffer?.put(chunk, 0, aligned)
                                encoder.queueInputBuffer(inIndex, 0, aligned, pts, 0)
                                pts += aligned.toLong() * 1_000_000L / (sampleRate.toLong() * frameSize)
                                processed += aligned
                            }
                        }
                    }
                    val outIndex = encoder.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            trackIndex = muxer.addTrack(encoder.outputFormat)
                            muxer.start()
                            muxerStarted = true
                        }
                        outIndex >= 0 -> {
                            val buffer = encoder.getOutputBuffer(outIndex)
                            if (buffer != null && muxerStarted && info.size > 0) {
                                if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                    buffer.position(info.offset)
                                    buffer.limit(info.offset + info.size)
                                    muxer.writeSampleData(trackIndex, buffer, info)
                                }
                            }
                            encoder.releaseOutputBuffer(outIndex, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputDone = true
                            }
                            val progress = (0.88f + (processed.toFloat() / totalBytes) * 0.1f).coerceIn(0.88f, 0.99f)
                            onProgress(progress, "编码中 ${(processed * 100 / totalBytes)}%")
                        }
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            runCatching { if (muxerStarted) muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    fun formatLabel(format: OutputFormat): String = format.label.lowercase(Locale.ROOT)
}
