package com.Huffer342.shiyin.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.*
import java.time.Instant
import java.time.ZoneId

@Composable
fun CountdownRemaining(end: Long, enabled: Boolean, total: Long) {
    val now by produceState(System.currentTimeMillis(), end, enabled) {
        value = System.currentTimeMillis()
        while (enabled && value < end) { kotlinx.coroutines.delay(250); value = System.currentTimeMillis() }
    }
    val remaining = if (enabled) ((end - now).coerceAtLeast(0) + 999) / 1000 else 0
    Text("${remaining} 秒", style = MaterialTheme.typography.displayMedium)
    Text(if (total > 0) "剩余 / 总计 ${total} 秒" else "剩余 / 总时长未记录",
        style = MaterialTheme.typography.labelLarge)
}

@Composable
fun CountdownDialog(songs: List<Song>, busy: Boolean, onDismiss: () -> Unit, onSave: (Schedule) -> Unit) {
    var seconds by rememberSaveable { mutableStateOf("20") }
    var minutes by rememberSaveable { mutableStateOf("1") }
    var uri by rememberSaveable { mutableStateOf(songs.firstOrNull()?.uri.orEmpty()) }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = songs.find { it.uri == uri }
    val delay = seconds.toLongOrNull()
    val stop = minutes.toIntOrNull()
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text("倒计时闹钟") },
        text = { LazyColumn(Modifier.heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(seconds, { seconds = it }, label = { Text("等待秒数（1～86400）") },
                singleLine = true, isError = delay == null || delay !in 1..86400) }
            item { OutlinedTextField(minutes, { minutes = it }, label = { Text("最多播放分钟（1～1440）") },
                singleLine = true, isError = stop == null || stop !in 1..1440) }
            item { Text("音乐：${selected?.title ?: "请先收录本地音乐"}") }
            item { OutlinedTextField(query, { query = it }, label = { Text("搜索本地歌曲") }, singleLine = true) }
            val choices = songs.filter { it.title.contains(query, true) }
            items(choices.size) { i ->
                val song = choices[i]
                ListItem(headlineContent = { Text(song.title) },
                    leadingContent = { RadioButton(uri == song.uri, onClick = { uri = song.uri }) },
                    modifier = Modifier.clickable { uri = song.uri })
            }
        } },
        confirmButton = { TextButton(enabled = !busy && selected != null && delay != null && delay in 1..86400 && stop != null && stop in 1..1440,
            onClick = {
                // shortcut: wall-clock deadline survives process death; add elapsed-time/reboot tracking for clock-edit resilience.
                val end = System.currentTimeMillis() + delay!! * 1000
                val date = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault())
                onSave(Schedule(title = selected!!.title, uri = selected.uri, hour = date.hour, minute = date.minute,
                    date = date.toLocalDate(), stopMinutes = stop!!, countdownAt = end, countdownSeconds = delay))
            }) { Text(if (busy) "保存中…" else "开始计时") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消") } })
}
