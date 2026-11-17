package com.Huffer342.shiyin.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*
import kotlinx.coroutines.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PlaybackVisuals(state: PlaybackUi, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pager = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    var waveform by remember(state.uri) { mutableStateOf<List<Float>?>(null) }
    var waveError by remember(state.uri) { mutableStateOf("") }
    var lyrics by remember(state.uri) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricError by remember(state.uri) { mutableStateOf("") }
    var importTarget by remember { mutableStateOf("") }
    val preferences = remember(context) { context.getSharedPreferences("lyrics", 0) }
    LaunchedEffect(state.uri) {
        lyrics = withContext(Dispatchers.IO) { parseLrc(preferences.getString(state.uri, "").orEmpty()) }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val target = importTarget
            scope.launch {
                try {
                    val parsed = withContext(Dispatchers.IO) {
                        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                            val buffer = CharArray(262145)
                            var count = 0
                            while (count < buffer.size) {
                                val n = reader.read(buffer, count, buffer.size - count)
                                if (n < 0) break
                                count += n
                            }
                            require(count <= 262144) { "歌词不能超过 256K 字符" }
                            String(buffer, 0, count)
                        } ?: error("无法读取歌词")
                        val lines = parseLrc(text)
                        require(lines.isNotEmpty()) { "请选择带时间标签的 LRC 歌词" }
                        check(preferences.edit().putString(target, text).commit()) { "歌词保存失败" }
                        lines
                    }
                    if (target == state.uri) { lyrics = parsed; lyricError = "" }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { lyricError = e.message ?: "歌词导入失败" }
            }
        }
    }
    LaunchedEffect(state.uri, pager.currentPage) {
        if (pager.currentPage == 1 && waveform == null && waveError.isEmpty()) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try { waveform = decodeWaveform(context, state.uri) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { waveError = e.message ?: "无法生成波形" }
                awaitCancellation()
            }
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("visual_pager")) { page ->
            when (page) {
                0 -> RecordArtwork(state.title, Modifier.fillMaxSize())
                1 -> Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("音频波形", style = MaterialTheme.typography.titleSmall)
                        val peaks = waveform
                        if (peaks == null) Text(waveError.ifEmpty { "正在分析音频…" })
                        else {
                            val colors = MaterialTheme.colorScheme
                            Canvas(Modifier.fillMaxWidth().weight(1f).padding(vertical = 16.dp).testTag("audio_waveform")) {
                                val maximum = peaks.maxOrNull()?.coerceAtLeast(.001f) ?: 1f
                                val fraction = (state.position.toFloat() / state.duration.coerceAtLeast(1)).coerceIn(0f, 1f)
                                peaks.forEachIndexed { index, peak ->
                                    val x = (index + .5f) / peaks.size * size.width
                                    val height = (peak / maximum * size.height * .46f).coerceAtLeast(1f)
                                    drawLine(if (index.toFloat() / peaks.size <= fraction) colors.primary else colors.outlineVariant,
                                        Offset(x, center.y - height), Offset(x, center.y + height),
                                        (size.width / peaks.size * .65f).coerceAtLeast(1f), StrokeCap.Round)
                                }
                                drawLine(colors.tertiary, Offset(size.width * fraction, 0f),
                                    Offset(size.width * fraction, size.height), 2.dp.toPx())
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("0:00", style = MaterialTheme.typography.labelSmall)
                                Text(durationText(state.duration), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { importTarget = state.uri; picker.launch(arrayOf("*/*")) }) { Text("导入 LRC 歌词") }
                    val current = lyrics.indexOfLast { it.timeMs <= state.position }
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (lyrics.isEmpty()) Text(lyricError.ifEmpty { "暂无歌词，可导入带时间标签的本地 LRC 文件" })
                        else {
                            if (lyricError.isNotEmpty()) Text(lyricError, color = MaterialTheme.colorScheme.error)
                            for (index in (current - 1).coerceAtLeast(0)..(current + 2).coerceAtMost(lyrics.lastIndex)) {
                                Text(lyrics[index].text, color = if (index == current) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = if (index == current) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            listOf("封面", "波形", "歌词").forEachIndexed { index, label ->
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(index) } }) {
                    Text(label, color = if (pager.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
