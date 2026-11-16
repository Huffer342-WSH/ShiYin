package com.Huffer342.shiyin.ui

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*
import kotlinx.coroutines.delay

@Composable
fun FolderBrowserScreen(
    scan: FolderScan, padding: PaddingValues, playlists: List<MusicPlaylist>, onExit: () -> Unit,
    onPlay: (Song, List<Song>) -> Unit,
    onAdd: (String, List<Song>, () -> Unit) -> Unit,
    onCreate: (String, List<Song>, () -> Unit) -> Unit
) {
    val root = remember(scan.folder.uri) { DocumentsContract.getTreeDocumentId(Uri.parse(scan.folder.uri)) }
    var directory by rememberSaveable(scan.folder.uri) { mutableStateOf(root) }
    var recursive by rememberSaveable(scan.folder.uri) { mutableStateOf(false) }
    var selected by rememberSaveable(scan.folder.uri) { mutableStateOf(emptyList<String>()) }
    var choosing by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val rows = remember(scan, directory, recursive) {
        if (recursive) scan.songs.map { FolderEntry(it.uri, root, it.title, it) }
        else scan.entries.filter { it.parentId == directory }
            .sortedWith(compareBy<FolderEntry> { it.song != null }.thenBy { it.name.lowercase() })
    }
    val songs = rows.mapNotNull { it.song }
    val currentSelection by rememberUpdatedState(selected)
    var dragStart by remember { mutableIntStateOf(-1) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragBase by remember { mutableStateOf(emptyList<String>()) }

    fun selectRange(index: Int) {
        if (dragStart >= 0 && index >= 0) {
            val range = minOf(dragStart, index)..maxOf(dragStart, index)
            selected = (dragBase + range.mapNotNull { rows.getOrNull(it)?.song?.uri }).distinct()
        }
    }
    fun hit(y: Float) = list.layoutInfo.visibleItemsInfo
        .firstOrNull { y >= it.offset && y < it.offset + it.size }?.index ?: -1
    fun clear() { selected = emptyList(); choosing = false; creating = false }
    LaunchedEffect(rows) {
        selected = selected.filter { uri -> songs.any { it.uri == uri } }
        if (directory != root && scan.entries.none { it.id == directory && it.song == null }) directory = root
    }
    LaunchedEffect(directory, recursive) { list.scrollToItem(0) }
    LaunchedEffect(dragStart) {
        while (dragStart >= 0) {
            val info = list.layoutInfo
            val edge = 72f
            val step = when {
                dragY < info.viewportStartOffset + edge -> -18f
                dragY > info.viewportEndOffset - edge -> 18f
                else -> 0f
            }
            if (step != 0f) {
                list.scrollBy(step)
                selectRange(hit(dragY.coerceIn(info.viewportStartOffset.toFloat(), (info.viewportEndOffset - 1).toFloat())))
            }
            delay(16)
        }
    }
    BackHandler(selected.isNotEmpty() || (!recursive && directory != root)) {
        if (selected.isNotEmpty()) clear()
        else directory = scan.entries.find { it.id == directory }?.parentId ?: root
    }
    Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("folder_browser")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!recursive, { recursive = false; clear() }, label = { Text("浏览") })
            FilterChip(recursive, { recursive = true; clear() }, label = { Text("全部音频") })
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, if (directory == root || recursive) "返回目录列表" else "上一级", {
                if (selected.isNotEmpty()) clear()
                else if (directory == root || recursive) onExit()
                else directory = scan.entries.find { it.id == directory }?.parentId ?: root
            })
            Text(if (directory == root || recursive) scan.folder.name else scan.entries.find { it.id == directory }?.name.orEmpty(),
                modifier = Modifier.weight(1f).padding(8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected.isNotEmpty()) {
                ActionIcon(Icons.Rounded.Close, "取消选择", { clear() })
                Text("已选 ${selected.size}", Modifier.weight(1f))
                TextButton(onClick = { selected = if (selected.size == songs.size) emptyList() else songs.map { it.uri } }) {
                    Text(if (selected.size == songs.size) "取消全选" else "全选")
                }
                ActionIcon(Icons.AutoMirrored.Rounded.PlaylistAdd, "添加到播放列表", { choosing = true })
            } else {
                Text("${songs.size} 首", Modifier.weight(1f).padding(8.dp))
                TextButton(onClick = { selected = songs.map { it.uri } }, enabled = songs.isNotEmpty()) { Text("全选") }
                ActionIcon(Icons.Rounded.PlayArrow, "播放当前视图", { songs.firstOrNull()?.let { onPlay(it, songs) } })
            }
        }
        if (scan.error.isNotEmpty()) Text(scan.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        if (rows.isEmpty()) Text("此处没有音频或子目录", Modifier.padding(24.dp))
        LazyColumn(state = list, userScrollEnabled = dragStart < 0, modifier = Modifier.weight(1f).testTag("folder_entries").pointerInput(rows) {
            detectDragGesturesAfterLongPress(
                onDragStart = { point ->
                    val index = hit(point.y)
                    if (rows.getOrNull(index)?.song != null) {
                        dragBase = currentSelection; dragStart = index; dragY = point.y; selectRange(index)
                    }
                },
                onDragEnd = { dragStart = -1 },
                onDragCancel = { dragStart = -1 },
                onDrag = { change, _ ->
                    if (dragStart >= 0) { change.consume(); dragY = change.position.y; selectRange(hit(dragY)) }
                })
        }) {
            itemsIndexed(rows, key = { _, entry -> entry.id }) { _, entry ->
                val song = entry.song
                val checked = song != null && song.uri in selected
                ListItem(
                    headlineContent = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { if (song != null) Text(song.artist, maxLines = 1) },
                    leadingContent = {
                        if (song != null && selected.isNotEmpty()) Checkbox(checked, null)
                        else Icon(if (song == null) Icons.Rounded.Folder else Icons.Rounded.MusicNote, null)
                    },
                    trailingContent = { if (song == null) Icon(Icons.Rounded.ChevronRight, null) },
                    modifier = Modifier.testTag("entry_${entry.name}").semantics {
                        if (song != null) {
                            this.selected = checked
                            onLongClick("选择音频") { selected = (selected + song.uri).distinct(); true }
                        }
                    }.clickable(onClick = {
                            if (song == null) { clear(); directory = entry.id }
                            else if (selected.isNotEmpty()) selected = if (checked) selected - song.uri else selected + song.uri
                            else onPlay(song, songs)
                        })
                )
            }
        }
    }
    val values = songs.filter { it.uri in selected }
    if (choosing && !creating) AddToPlaylistDialog(playlists, { choosing = false },
        { id -> onAdd(id, values) { clear() } }, { creating = true })
    if (creating) PlaylistNameDialog("新建播放列表", onDismiss = { creating = false }) { name ->
        onCreate(name, values) { clear() }
    }
}
