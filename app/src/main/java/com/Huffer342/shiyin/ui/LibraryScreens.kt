package com.Huffer342.shiyin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*

@Composable
fun PlaylistListScreen(playlists: List<MusicPlaylist>, padding: PaddingValues, onOpen: (String) -> Unit, onCreate: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("playlist_list"), contentPadding = padding) {
        if (playlists.isEmpty()) item {
            EmptyState(Icons.AutoMirrored.Rounded.PlaylistPlay, "没有播放列表", "", "新建播放列表", onCreate)
        }
        items(playlists, key = { it.id }) { list ->
            ListItem(headlineContent = { Text(list.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${list.songs.size} 首") },
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, Modifier.padding(8.dp).size(32.dp)) },
                trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                modifier = Modifier.clickable { onOpen(list.id) })
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun PlaylistDetailScreen(
    playlist: MusicPlaylist, padding: PaddingValues, onPlay: (Song, Boolean) -> Unit,
    onRemove: (Song) -> Unit, onMove: (Int, Int) -> Unit, onManage: () -> Unit,
    onQueue: (Song, Boolean) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding), contentPadding = padding) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { playlist.songs.firstOrNull()?.let { onPlay(it, false) } }, enabled = playlist.songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, null); Text("播放")
                }
                OutlinedButton(onClick = { playlist.songs.randomOrNull()?.let { onPlay(it, true) } }, enabled = playlist.songs.isNotEmpty()) {
                    Icon(Icons.Rounded.Shuffle, null); Text("随机")
                }
                IconButton(onClick = onManage) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "管理歌曲") }
            }
        }
        if (playlist.songs.isEmpty()) item {
            EmptyState(Icons.Rounded.MusicNote, "列表为空", "", "添加歌曲", onManage)
        }
        items(playlist.songs.size, key = { playlist.songs[it].uri }) { index ->
            val song = playlist.songs[index]
            var menu by remember { mutableStateOf(false) }
            ListItem(headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(song.artist, maxLines = 1) },
                leadingContent = { Text("${index + 1}", style = MaterialTheme.typography.labelLarge) },
                trailingContent = {
                    Box {
                        ActionIcon(Icons.Rounded.MoreVert, "${song.title}，列表操作", { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("下一首播放") }, onClick = { menu = false; onQueue(song, true) })
                            DropdownMenuItem(text = { Text("加入队列") }, onClick = { menu = false; onQueue(song, false) })
                            DropdownMenuItem(text = { Text("上移") }, enabled = index > 0, onClick = { menu = false; onMove(index, index - 1) })
                            DropdownMenuItem(text = { Text("下移") }, enabled = index < playlist.songs.lastIndex,
                                onClick = { menu = false; onMove(index, index + 1) })
                            DropdownMenuItem(text = { Text("从列表移除") }, onClick = { menu = false; onRemove(song) })
                        }
                    }
                }, modifier = Modifier.clickable { onPlay(song, false) })
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun FolderListScreen(
    systemEnabled: Boolean,
    systemPermission: Boolean,
    systemSongCount: Int,
    mediaDirectories: List<MediaStoreDirectory>,
    systemError: String,
    folders: List<WatchedFolder>,
    scans: List<FolderScan>,
    padding: PaddingValues,
    onSystemEnabledChange: (Boolean) -> Unit,
    onOpenSystem: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onRemove: (WatchedFolder) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("folder_list"), contentPadding = padding) {
        item(key = "system_library") {
            val excluded = mediaDirectories.count { !it.included }
            val status = when {
                !systemEnabled -> "已关闭"
                !systemPermission -> "未授权，点击管理"
                systemError.isNotEmpty() -> systemError
                else -> "${systemSongCount} 首 · 已排除 ${excluded} 个目录"
            }
            ListItem(
                headlineContent = { Text("系统音乐库") },
                supportingContent = {
                    Text(status, color = if (systemError.isNotEmpty()) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingContent = { Icon(Icons.Rounded.LibraryMusic, null, Modifier.padding(8.dp).size(32.dp)) },
                trailingContent = {
                    Switch(
                        checked = systemEnabled,
                        onCheckedChange = onSystemEnabledChange,
                        modifier = Modifier.semantics { contentDescription = "系统音乐库开关" }
                            .testTag("system_library_switch")
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenSystem).testTag("system_library")
            )
            HorizontalDivider()
        }
        items(folders, key = { it.uri }) { folder ->
            val scan = scans.find { it.folder.uri == folder.uri }
            var menu by remember { mutableStateOf(false) }
            ListItem(headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Text(scan?.error?.takeIf { it.isNotEmpty() } ?: "${scan?.songs?.size ?: 0} 首",
                        color = if (scan?.error?.isNotEmpty() == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                leadingContent = { Icon(Icons.Rounded.Folder, null, Modifier.padding(8.dp).size(32.dp)) },
                trailingContent = {
                    Box {
                        ActionIcon(Icons.Rounded.MoreVert, "${folder.name}，目录操作", { menu = true })
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("重新授权") }, onClick = { menu = false; onAdd() })
                            DropdownMenuItem(text = { Text("停止监控") }, onClick = { menu = false; onRemove(folder) })
                        }
                    }
                }, modifier = Modifier.clickable { onOpen(folder.uri) })
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun MediaStoreDirectoryScreen(
    enabled: Boolean,
    permission: Boolean,
    directories: List<MediaStoreDirectory>,
    padding: PaddingValues,
    onEnable: () -> Unit,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onIncludedChange: (MediaStoreDirectory, Boolean) -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("system_library_directories"),
        contentPadding = padding
    ) {
        when {
            !enabled -> item {
                EmptyState(Icons.Rounded.LibraryMusic, "系统音乐库已关闭", "", "开启", onEnable)
            }
            !permission -> item {
                EmptyState(Icons.Rounded.LibraryMusic, "需要音乐权限", "授权后才能读取和排除系统音乐目录", "允许访问", onRequestPermission)
            }
            directories.isEmpty() -> item {
                EmptyState(Icons.Rounded.LibraryMusic, "系统音乐库没有音频", "", "重新扫描", onRefresh)
            }
            else -> items(directories, key = { it.key }) { directory ->
                val volume = when (directory.volumeName) {
                    "external_primary" -> "手机存储"
                    "external" -> "共享存储"
                    "legacy" -> "设备存储"
                    else -> directory.volumeName
                }
                ListItem(
                    headlineContent = { Text(directory.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("$volume · ${directory.songCount} 首") },
                    leadingContent = { Icon(Icons.Rounded.Folder, null) },
                    trailingContent = {
                        Switch(
                            checked = directory.included,
                            onCheckedChange = { onIncludedChange(directory, it) },
                            modifier = Modifier.semantics { contentDescription = "收录 ${directory.path}" }
                                .testTag("media_directory_${directory.key}")
                        )
                    }
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun PlaylistNameDialog(title: String, initial: String = "", onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(value, { value = it; error = false }, singleLine = true,
            label = { Text("名称") }, isError = error, modifier = Modifier.testTag("playlist_name"),
            supportingText = { if (error) Text("请输入 1–64 个字符") }) },
        confirmButton = { TextButton(onClick = {
            if (value.trim().length !in 1..64) error = true else onConfirm(value.trim())
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSongsSheet(playlist: MusicPlaylist, library: List<Song>, onDismiss: () -> Unit, onSave: (List<Song>) -> Unit) {
    val available = remember(playlist, library) { (playlist.songs + library).distinctBy { it.uri } }
    var selected by rememberSaveable { mutableStateOf(playlist.songs.map { it.uri }) }
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("管理歌曲", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { onSave(available.filter { it.uri in selected }) }) { Text("完成（${selected.size}）") }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, placeholder = { Text("搜索歌曲") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.heightIn(max = 480.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(available.filter { it.title.contains(query, true) || it.artist.contains(query, true) }, key = { it.uri }) { song ->
                ListItem(headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(song.artist, maxLines = 1) },
                    leadingContent = { Checkbox(song.uri in selected, null) },
                    modifier = Modifier.clickable {
                        selected = if (song.uri in selected) selected - song.uri else selected + song.uri
                    })
            }
        }
    }
}

@Composable
fun AddToPlaylistDialog(playlists: List<MusicPlaylist>, onDismiss: () -> Unit, onChoose: (String) -> Unit, onCreate: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加到播放列表") },
        text = { LazyColumn(Modifier.heightIn(max = 360.dp)) {
            items(playlists, key = { it.id }) { list -> TextButton(onClick = { onChoose(list.id) }) { Text(list.name) } }
        } }, confirmButton = { TextButton(onClick = onCreate) { Text("新建播放列表") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(remaining: Long, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var minutes by rememberSaveable { mutableStateOf("30") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("睡眠定时") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (remaining > 0) Text("剩余 ${durationText((remaining + 999) / 1000 * 1000)}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60).forEach { value ->
                    FilterChip(minutes == value.toString(), { minutes = value.toString() }, label = { Text("$value 分") })
                }
            }
            OutlinedTextField(minutes, { minutes = it; error = false }, label = { Text("分钟") }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                isError = error, supportingText = { if (error) Text("请输入 1–1440 分钟") })
        } }, confirmButton = { TextButton(onClick = {
            val value = minutes.toIntOrNull()
            if (value == null || value !in 1..1440) error = true else onSet(value)
        }) { Text("开始计时") } },
        dismissButton = { TextButton(onClick = { if (remaining > 0) onSet(0) else onDismiss() }) {
            Text(if (remaining > 0) "取消定时" else "取消")
        } })
}
