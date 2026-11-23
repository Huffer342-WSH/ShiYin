package com.Huffer342.shiyin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.AudioDetails
import com.Huffer342.shiyin.MusicLibrary
import com.Huffer342.shiyin.PlaybackUi
import com.Huffer342.shiyin.Song
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun RecordArtwork(seed: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val pair = when (Math.floorMod(seed.hashCode(), 3)) {
        0 -> colors.primaryContainer to colors.onPrimaryContainer
        1 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Canvas(modifier.clip(MaterialTheme.shapes.medium).background(pair.first)) {
        val radius = size.minDimension * .36f
        drawCircle(pair.second.copy(alpha = .88f), radius)
        for (fraction in listOf(.52f, .66f, .8f, .94f)) drawCircle(pair.first.copy(alpha = .25f),
            radius * fraction, style = Stroke(size.minDimension * .004f))
        drawCircle(pair.first, radius * .31f)
        drawCircle(pair.second, radius * .07f)
        drawLine(pair.first.copy(alpha = .55f), center + Offset(radius * .48f, -radius * .48f),
            center + Offset(radius * .72f, -radius * .72f), size.minDimension * .025f)
    }
}

@Composable
fun ActionIcon(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription = label) }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun LibraryScreen(
    songs: List<Song>, playback: PlaybackUi, permission: Boolean, connected: Boolean,
    busy: Boolean, padding: PaddingValues, listState: LazyListState,
    query: String, onQuery: (String) -> Unit, onScan: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit, onSchedule: (Song) -> Unit,
    onPlaylist: (Song) -> Unit, onQueue: (Song, Boolean) -> Unit,
    searchVisible: Boolean = true, sourceError: String = ""
) {
    val filtered = remember(songs, query) { songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) } }
    var detailsSong by remember { mutableStateOf<Song?>(null) }
    val listModifier = Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("library_list")
        .then(if (detailsSong == null) Modifier else Modifier.clearAndSetSemantics {})
    LazyColumn(state = listState, modifier = listModifier,
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp)) {
        if (searchVisible) item {
            OutlinedTextField(query, onQuery, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("library_search"),
                placeholder = { Text("搜索歌曲、艺术家") }, singleLine = true, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) ActionIcon(Icons.Rounded.Close, "清空搜索", { onQuery("") }) })
        }
        if (sourceError.isNotEmpty()) item { Text(sourceError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        if (filtered.isNotEmpty()) item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} 首", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionIcon(Icons.Rounded.PlayArrow, "播放全部", { onPlay(filtered.first(), filtered) }, connected)
            }
        }
        if (filtered.isEmpty()) item {
            EmptyState(Icons.Rounded.MusicNote, if (query.isEmpty()) "没有音频" else "没有匹配的歌曲", "",
                if (query.isNotEmpty()) "清空搜索" else if (permission) "重新扫描" else "选择目录",
                { if (query.isNotEmpty()) onQuery("") else if (!busy) onScan() })
        }
        items(filtered, key = { it.uri }) { song ->
            var menu by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${song.artist} · ${durationText(song.duration)}",
                    maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { RecordArtwork(song.title, Modifier.size(48.dp)) },
                trailingContent = {
                    Box {
                        ActionIcon(Icons.Rounded.MoreVert, "${song.title}，更多操作", { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("下一首播放") }, enabled = !playback.alarm, onClick = { menu = false; onQueue(song, true) })
                            DropdownMenuItem(text = { Text("加入队列") }, enabled = !playback.alarm, onClick = { menu = false; onQueue(song, false) })
                            DropdownMenuItem(text = { Text("添加到播放列表") }, onClick = { menu = false; onPlaylist(song) })
                            DropdownMenuItem(text = { Text("创建闹钟") }, onClick = { menu = false; onSchedule(song) })
                            DropdownMenuItem(text = { Text("详细信息") }, onClick = {
                                menu = false
                                detailsSong = song
                            })
                        }
                    }
                },
                colors = ListItemDefaults.colors(containerColor = if (playback.uri == song.uri)
                    MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable(enabled = connected) { onPlay(song, filtered) })
        }
    }
    detailsSong?.let { song -> AudioDetailsDialog(song) { detailsSong = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioDetailsDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var details by remember(song.uri) { mutableStateOf<AudioDetails?>(null) }
    var error by remember(song.uri) { mutableStateOf("") }
    var confirmFileManager by remember { mutableStateOf(false) }
    LaunchedEffect(song.uri) {
        runCatching { withContext(Dispatchers.IO) { MusicLibrary.audioDetails(context, song) } }
            .onSuccess { details = it }
            .onFailure { error = it.message ?: "无法读取音频信息" }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("音频详细信息") },
        text = {
            when {
                details != null -> Column(
                    Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AudioDetailRow("标题", song.title)
                    AudioDetailRow("艺术家", song.artist)
                    AudioDetailRow("时长", durationText(song.duration))
                    AudioDetailRow("来源", details!!.source)
                    AudioDetailRow("文件名", details!!.fileName)
                    AudioDetailRow(
                        "位置",
                        details!!.location,
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                            .combinedClickable(
                                onClick = {
                                    context.getSystemService(ClipboardManager::class.java)
                                        .setPrimaryClip(ClipData.newPlainText("音频位置", details!!.location))
                                    Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = { confirmFileManager = true }
                            )
                            .semantics { contentDescription = "音频位置，点击复制，长按打开文件管理器" }
                            .testTag("audio_location")
                    )
                    AudioDetailRow("格式", audioFormatText(details!!.fileName, details!!.mimeType))
                    AudioDetailRow("大小", audioSizeText(details!!.sizeBytes))
                    AudioDetailRow("比特率", audioBitrateText(details!!.bitrate))
                }
                error.isNotEmpty() -> Text(error, color = MaterialTheme.colorScheme.error)
                else -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
    if (confirmFileManager) AlertDialog(
        onDismissRequest = { confirmFileManager = false },
        title = { Text("打开文件管理器？") },
        text = { Text("将离开时音，并在系统文件管理器中打开音频所在目录。") },
        confirmButton = {
            TextButton(onClick = {
                confirmFileManager = false
                runCatching { context.startActivity(MusicLibrary.fileManagerIntent(requireNotNull(details))) }
                    .onFailure { Toast.makeText(context, "无法打开文件管理器", Toast.LENGTH_SHORT).show() }
            }) { Text("打开") }
        },
        dismissButton = { TextButton(onClick = { confirmFileManager = false }) { Text("取消") } }
    )
}

@Composable
private fun AudioDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

fun audioSizeText(bytes: Long): String = when {
    bytes < 0 -> "未知"
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
}

fun audioBitrateText(bitsPerSecond: Long): String =
    if (bitsPerSecond <= 0) "未知" else "${(bitsPerSecond + 500) / 1000} kbps"

fun audioFormatText(fileName: String, mimeType: String): String {
    val extension = fileName.substringAfterLast('.', "").uppercase(Locale.ROOT)
    return when {
        extension.isNotEmpty() && mimeType.isNotEmpty() -> "$extension · $mimeType"
        extension.isNotEmpty() -> extension
        mimeType.isNotEmpty() -> mimeType
        else -> "未知"
    }
}

@Composable
fun MiniPlayer(state: PlaybackUi, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().testTag("mini_player")) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                RecordArtwork(state.title, Modifier.size(42.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(state.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.sleepRemaining > 0) "定时关闭 · ${durationText(state.sleepRemaining)}" else state.artist,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ActionIcon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playing) "暂停播放" else "继续播放", onToggle)
                ActionIcon(Icons.Rounded.SkipNext, "下一首", onNext, !state.alarm)
            }
            if (state.duration > 0) LinearProgressIndicator(
                progress = { (state.position.toFloat() / state.duration).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp), trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlaybackUi, onBack: () -> Unit, onToggle: () -> Unit, onStop: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSeek: (Long) -> Unit,
    onRepeat: () -> Unit, onShuffle: () -> Unit, onQueue: () -> Unit, onSchedule: () -> Unit,
    onSleep: () -> Unit, onSaveQueue: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        CenterAlignedTopAppBar(title = { Text("正在播放", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onBack) },
            actions = {
                ActionIcon(Icons.AutoMirrored.Rounded.QueueMusic, "播放队列", onQueue)
                Box {
                    ActionIcon(Icons.Rounded.MoreVert, "播放选项", { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("睡眠定时") }, enabled = !state.alarm, onClick = { menu = false; onSleep() })
                        DropdownMenuItem(text = { Text("保存播放列表") }, onClick = { menu = false; onSaveQueue() })
                        DropdownMenuItem(text = { Text("创建闹钟") }, onClick = { menu = false; onSchedule() })
                        DropdownMenuItem(text = { Text("停止播放") }, onClick = { menu = false; onStop() })
                    }
                }
            })
    }) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            val wide = maxWidth >= 600.dp
            val artwork = if (wide) 220.dp else minOf(maxWidth - 64.dp, maxHeight * .42f, 320.dp)
            if (wide) Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(40.dp)) {
                key(state.uri) { PlaybackVisuals(state, Modifier.size(artwork)) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    PlaybackDetails(state, onToggle, onPrevious, onNext, onSeek, onRepeat, onShuffle, onSleep)
                }
            } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                key(state.uri) { PlaybackVisuals(state, Modifier.size(artwork)) }
                Spacer(Modifier.height(28.dp))
                PlaybackDetails(state, onToggle, onPrevious, onNext, onSeek, onRepeat, onShuffle, onSleep)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackDetails(state: PlaybackUi, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, onRepeat: () -> Unit, onShuffle: () -> Unit, onSleep: () -> Unit) {
    var dragging by remember(state.uri) { mutableStateOf<Float?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(state.title, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(state.artist, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.sleepRemaining > 0) TextButton(onClick = onSleep) {
            Icon(Icons.Rounded.Bedtime, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp)); Text(durationText(state.sleepRemaining), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.alarmRemaining > 0) Text("闹钟 · ${durationText(state.alarmRemaining)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = dragging ?: state.position.toFloat().coerceIn(0f, state.duration.coerceAtLeast(1).toFloat()),
            onValueChange = { dragging = it }, onValueChangeFinished = { dragging?.let { onSeek(it.toLong()) }; dragging = null },
            valueRange = 0f..state.duration.coerceAtLeast(1).toFloat(), enabled = state.duration > 0,
            thumb = { Surface(Modifier.size(20.dp), shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary, shadowElevation = 3.dp,
                border = androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.surface)) {} },
            track = { sliderState -> SliderDefaults.Track(sliderState, Modifier.height(8.dp)) },
            colors = SliderDefaults.colors(activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().height(56.dp).testTag("playback_seek").semantics { contentDescription = "播放进度" })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(durationText(dragging?.toLong() ?: state.position), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.testTag("seek_time"))
            Text(durationText(state.duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            IconToggleButton(state.shuffle, { onShuffle() }, enabled = !state.alarm) { Icon(Icons.Rounded.Shuffle, "随机播放") }
            ActionIcon(Icons.Rounded.SkipPrevious, "上一首", onPrevious, !state.alarm)
            FilledIconButton(onToggle, Modifier.size(52.dp)) {
                Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (state.playing) "暂停播放" else "继续播放", Modifier.size(30.dp))
            }
            ActionIcon(Icons.Rounded.SkipNext, "下一首", onNext, !state.alarm)
            IconToggleButton(state.repeat != 0, { onRepeat() }, enabled = !state.alarm) {
                Icon(if (state.repeat == 1) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    when (state.repeat) { 1 -> "单曲循环"; 2 -> "列表循环"; else -> "顺序播放" })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSheet(title: String, songs: List<Song>, onDismiss: () -> Unit, onSelect: (Song) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(20.dp),
            singleLine = true, placeholder = { Text("搜索歌曲") }, leadingIcon = { Icon(Icons.Rounded.Search, null) })
        LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }, key = { it.uri }) { song ->
                ListItem(headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(song.artist, maxLines = 1) },
                    leadingContent = { RecordArtwork(song.title, Modifier.size(40.dp)) },
                    modifier = Modifier.clickable { onSelect(song) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlaybackUi, onDismiss: () -> Unit, onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit, onMove: (Int, Int) -> Unit, onSave: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("播放队列 · ${state.queue.size}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ActionIcon(Icons.Rounded.Save, "保存播放列表", onSave, state.queue.isNotEmpty())
        }
        LazyColumn(Modifier.heightIn(max = 440.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(state.queue.size) { index ->
                var menu by remember { mutableStateOf(false) }
                ListItem(headlineContent = { Text(state.queue[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { if (index == state.index) Icon(Icons.Rounded.GraphicEq, "当前歌曲")
                        else Text("${index + 1}", style = MaterialTheme.typography.labelLarge) },
                    trailingContent = {
                        Box {
                            ActionIcon(Icons.Rounded.MoreVert, "队列操作 ${index + 1}", { menu = true }, !state.alarm)
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("上移") }, enabled = index > 0, onClick = { menu = false; onMove(index, index - 1) })
                                DropdownMenuItem(text = { Text("下移") }, enabled = index < state.queue.lastIndex,
                                    onClick = { menu = false; onMove(index, index + 1) })
                                DropdownMenuItem(text = { Text("移除") }, onClick = { menu = false; onRemove(index) })
                            }
                        }
                    }, modifier = Modifier.clickable { onSelect(index) })
            }
        }
    }
}

fun durationText(ms: Long): String = String.format(Locale.ROOT, "%d:%02d", ms / 60_000, ms / 1000 % 60)

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun MiniPlayerPreview() {
    ShiYinTheme { MiniPlayer(PlaybackUi(title = "歌曲", artist = "艺术家", playing = true), {}, {}, {}) }
}
