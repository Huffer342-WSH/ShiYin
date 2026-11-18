package com.Huffer342.shiyin

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

data class LyricLine(val timeMs: Long, val text: String)

fun parseLrc(text: String): List<LyricLine> {
    require(text.length <= 1_000_000) { "歌词文件过大" }
    val stamp = Regex("\\[(\\d{1,4}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val offset = Regex("\\[offset:([+-]?\\d+)]").find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    return text.lineSequence().flatMap { line ->
        val words = line.replace(stamp, "").trim()
        stamp.findAll(line).mapNotNull { match ->
            val seconds = match.groupValues[2].toInt()
            if (seconds >= 60) null else {
                val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLong()
                LyricLine((match.groupValues[1].toLong() * 60_000 + seconds * 1000 + fraction + offset).coerceAtLeast(0), words)
            }
        }
    }.sortedBy { it.timeMs }.toList()
}

/** Fixed-size peak envelope of decoded PCM, not a microphone or decorative animation. */
suspend fun decodeWaveform(context: Context, uri: String): List<Float> = withContext(Dispatchers.IO) {
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(context, Uri.parse(uri), null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("没有可解码的音轨")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val duration = format.getLong(MediaFormat.KEY_DURATION)
        require(duration > 0) { "无法读取音频时长" }
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start()
        val peaks = FloatArray(240)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        val deadline = android.os.SystemClock.elapsedRealtime() + 60_000
        while (!outputDone) {
            coroutineContext.ensureActive()
            check(android.os.SystemClock.elapsedRealtime() < deadline) { "波形分析超时，音频仍可正常播放" }
            if (!inputDone) {
                val input = decoder.dequeueInputBuffer(10_000)
                if (input >= 0) {
                    val buffer = decoder.getInputBuffer(input)!!
                    val length = extractor.readSampleData(buffer, 0)
                    if (length < 0) {
                        decoder.queueInputBuffer(input, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        decoder.queueInputBuffer(input, 0, length, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val output = decoder.dequeueOutputBuffer(info, 10_000)
            if (output == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val f = decoder.outputFormat
                rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                encoding = if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else AudioFormat.ENCODING_PCM_16BIT
                require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT) { "暂不支持此 PCM 波形格式" }
            } else if (output >= 0) {
                val buffer = decoder.getOutputBuffer(output)!!.order(ByteOrder.nativeOrder())
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                var sample = 0L
                val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                while (buffer.remaining() >= bytes) {
                    val amplitude = if (bytes == 4) abs(buffer.float).takeIf { it.isFinite() } ?: 0f
                        else abs(buffer.short.toInt()) / 32768f
                    val time = info.presentationTimeUs + sample / channels * 1_000_000 / rate
                    val bin = (time.toDouble() / duration * peaks.size).toInt().coerceIn(peaks.indices)
                    peaks[bin] = maxOf(peaks[bin], amplitude.coerceIn(0f, 1f))
                    sample++
                }
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                decoder.releaseOutputBuffer(output, false)
            }
        }
        peaks.toList()
    } finally {
        codec?.let { runCatching { it.stop() }; it.release() }
        extractor.release()
    }
}
