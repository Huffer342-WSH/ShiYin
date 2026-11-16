package com.Huffer342.shiyin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.Huffer342.shiyin.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>, busy: Boolean, exactAllowed: Boolean, padding: PaddingValues,
    listState: LazyListState, onAdd: () -> Unit, onEdit: (Schedule) -> Unit,
    onToggle: (Schedule, Boolean) -> Unit, onDelete: (Schedule) -> Unit,
    onPreview: (Schedule) -> Unit, onPermission: () -> Unit
) {
    val active = schedules.count { it.enabled && it.nextAt > 0 }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().consumeWindowInsets(padding).testTag("schedule_list"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(if (active > 0) "$active 个已开启" else "",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp))
        }
        if (!exactAllowed) item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("还需要闹钟权限", style = MaterialTheme.typography.titleMedium)
                    Text("尚未授权", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = onPermission) { Text("前往授权") }
                }
            }
        }
        if (schedules.isEmpty()) item {
            EmptyState(Icons.Rounded.Alarm, "没有闹钟", "", "添加闹钟", onAdd)
        }
        items(schedules, key = { it.id }) { schedule ->
            var menu by remember { mutableStateOf(false) }
            Card(onClick = { onEdit(schedule) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor =
                    if (schedule.enabled) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(timeText(schedule.hour, schedule.minute), style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = 44.sp, fontWeight = FontWeight.Medium),
                            color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        Switch(checked = schedule.enabled, enabled = !busy,
                            onCheckedChange = { onToggle(schedule, it) },
                            modifier = Modifier.semantics { contentDescription = "${schedule.title}，启用定时" })
                    }
                    Text(ruleText(schedule), style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RecordArtwork(schedule.title, Modifier.size(40.dp))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(schedule.title, style = MaterialTheme.typography.titleSmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("播放 ${schedule.stopMinutes} 分钟", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            ActionIcon(Icons.Rounded.MoreVert, "${schedule.title}，任务操作", { menu = true })
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("试听") }, leadingIcon = { Icon(Icons.Rounded.Headphones, null) },
                                    onClick = { menu = false; onPreview(schedule) })
                                DropdownMenuItem(text = { Text("编辑") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                    onClick = { menu = false; onEdit(schedule) })
                                DropdownMenuItem(text = { Text("删除") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                    onClick = { menu = false; onDelete(schedule) }, enabled = !busy)
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(if (schedule.nextAt > 0) "下次 · ${Instant.ofEpochMilli(schedule.nextAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))}" else schedule.status.ifBlank { "尚未调度" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (schedule.enabled && schedule.nextAt == 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditorScreen(
    original: Schedule?, initialSong: Song, songs: List<Song>, busy: Boolean,
    previewing: (String) -> Boolean, calendarYears: List<Int>,
    onBack: () -> Unit, onPreview: (Song, Int) -> Unit, onStopPreview: () -> Unit,
    onSave: (Schedule) -> Unit
) {
    val initialHour = original?.hour ?: 7
    val initialMinute = original?.minute ?: 0
    val initialDate = (original?.date ?: LocalDate.now().plusDays(1)).toString()
    val initialRule = original?.rule ?: RepeatRule.ONCE
    val initialDays = original?.weekdays?.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) } ?: 31
    var uri by rememberSaveable { mutableStateOf(initialSong.uri) }
    var title by rememberSaveable { mutableStateOf(initialSong.title) }
    var hour by rememberSaveable { mutableIntStateOf(initialHour) }
    var minute by rememberSaveable { mutableIntStateOf(initialMinute) }
    var dateText by rememberSaveable { mutableStateOf(initialDate) }
    var duration by rememberSaveable { mutableStateOf((original?.stopMinutes ?: 30).toString()) }
    var fade by rememberSaveable { mutableStateOf((original?.fadeSeconds ?: 15).toString()) }
    var ruleName by rememberSaveable { mutableStateOf(initialRule.name) }
    var dayMask by rememberSaveable { mutableIntStateOf(initialDays) }
    var chooseSong by rememberSaveable { mutableStateOf(false) }
    var chooseTime by rememberSaveable { mutableStateOf(false) }
    var chooseDate by rememberSaveable { mutableStateOf(false) }
    var discard by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val compactPicker = configuration.screenHeightDp < 600 || configuration.fontScale >= 1.3f
    val rule = RepeatRule.valueOf(ruleName)
    val days = DayOfWeek.entries.filter { dayMask and (1 shl (it.value - 1)) != 0 }.toSet()
    val selected = songs.find { it.uri == uri } ?: Song(uri, title, "", 0)
    val dirty = uri != initialSong.uri || hour != initialHour || minute != initialMinute ||
        dateText != initialDate || duration != (original?.stopMinutes ?: 30).toString() ||
        ruleName != initialRule.name || dayMask != initialDays || fade != (original?.fadeSeconds ?: 15).toString()
    val close = { if (!busy) { if (dirty) discard = true else onBack() } }
    BackHandler { close() }

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(title = { Text(if (original == null) "添加闹钟" else "编辑闹钟") },
                navigationIcon = { ActionIcon(Icons.AutoMirrored.Rounded.ArrowBack, "取消编辑", close, !busy) },
                actions = {
                    TextButton(enabled = !busy, modifier = Modifier.testTag("save_schedule"), onClick = {
                        val minutes = duration.toIntOrNull()
                        val fadeTime = fade.toIntOrNull()
                        error = when {
                            minutes == null || minutes !in 1..1440 -> "播放时长须为 1–1440 分钟"
                            fadeTime == null || fadeTime !in 0..300 || fadeTime >= minutes * 60 -> "淡入须为 0–300 秒且小于时长上限"
                            rule == RepeatRule.WEEKLY && days.isEmpty() -> "请至少选择一个星期"
                            rule == RepeatRule.ONCE && !LocalDate.parse(dateText).atTime(hour, minute)
                                .atZone(ZoneId.systemDefault()).toInstant().isAfter(Instant.now()) -> "请选择未来的日期和时间"
                            else -> ""
                        }
                        if (error.isEmpty() && minutes != null && fadeTime != null) {
                            keyboard?.hide()
                            val value = Schedule(title = selected.title, uri = selected.uri, hour = hour, minute = minute,
                                rule = rule, date = LocalDate.parse(dateText), weekdays = days, stopMinutes = minutes, fadeSeconds = fadeTime)
                            onSave(if (original == null) value else value.copy(id = original.id, enabled = original.enabled))
                        }
                    }) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("保存") }
                })
        }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp)
                .consumeWindowInsets(padding).testTag("schedule_editor"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Card(onClick = { chooseTime = true }, modifier = Modifier.fillMaxWidth().testTag("choose_time"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("开始播放", style = MaterialTheme.typography.labelLarge)
                            Text(timeText(hour, minute), style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Medium))

                        }
                        Icon(Icons.Rounded.AccessTime, null, Modifier.size(32.dp))
                    }
                }
            }
            item {
                SectionLabel("播放内容")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    ListItem(headlineContent = { Text(selected.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("选择本地歌曲") },
                        leadingContent = { RecordArtwork(selected.title, Modifier.size(48.dp)) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.clickable { chooseSong = true }.testTag("choose_song"))
                    TextButton(onClick = {
                        if (previewing(uri)) onStopPreview() else {
                            val seconds = fade.toIntOrNull()
                            if (seconds == null || seconds !in 0..300) error = "淡入须为 0–300 秒"
                            else onPreview(selected, seconds)
                        }
                    }, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Icon(if (previewing(uri)) Icons.Rounded.Stop else Icons.Rounded.Headphones, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (previewing(uri)) "停止试听" else "试听")
                    }
                }
            }
            item {
                SectionLabel("重复")
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    RepeatRule.entries.forEach { option ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = rule == option, role = Role.RadioButton,
                            onClick = { ruleName = option.name }).padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = rule == option, onClick = null)
                            Text(option.label, Modifier.padding(start = 12.dp))
                        }
                    }
                    if (rule == RepeatRule.WEEKLY) FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            FilterChip(selected = day in days, onClick = {
                                dayMask = dayMask xor (1 shl (day.value - 1))
                            }, label = { Text("周${weekdayName(day)}") })
                        }
                    }
                    if (rule == RepeatRule.WORKDAY) Text(
                        if (calendarYears.isEmpty()) "未下载日历，请在设置中更新"
                        else "日历：${calendarYears.joinToString()} 年",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (rule == RepeatRule.ONCE) ListItem(
                        headlineContent = { Text(LocalDate.parse(dateText).format(DateTimeFormatter.ofPattern("yyyy年M月d日"))) },
                        leadingContent = { Icon(Icons.Rounded.CalendarMonth, null) },
                        trailingContent = { Icon(Icons.Rounded.ChevronRight, null) },
                        modifier = Modifier.clickable { chooseDate = true }.testTag("choose_date"),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
                }
            }
            item {
                SectionLabel("单次时长上限")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        FilterChip(selected = duration == minutes.toString(),
                            onClick = { duration = minutes.toString() }, label = { Text("$minutes 分钟") })
                    }
                }
                OutlinedTextField(duration, { duration = it }, Modifier.fillMaxWidth().testTag("duration"),
                    label = { Text("最多播放") }, suffix = { Text("分钟") }, singleLine = true,

                    isError = error.startsWith("播放时长"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            }
            if (error.isNotBlank()) item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("editor_error")) }
            item {
                SectionLabel("淡入")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0, 5, 15, 30, 60).forEach { seconds ->
                        FilterChip(fade == seconds.toString(), { fade = seconds.toString() },
                            label = { Text(if (seconds == 0) "关闭" else "$seconds 秒") })
                    }
                }
                OutlinedTextField(fade, { fade = it }, Modifier.fillMaxWidth().testTag("fade_seconds"),
                    label = { Text("淡入时长") }, suffix = { Text("秒") }, singleLine = true,
                    isError = error.startsWith("淡入"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }))
            }
        }
    }
    if (chooseSong) SongSheet("选择音乐", songs, { chooseSong = false }) {
        uri = it.uri; title = it.title; chooseSong = false
    }
    if (chooseTime) {
        val state = rememberTimePickerState(hour, minute, is24Hour = true)
        var input by rememberSaveable { mutableStateOf(false) }
        val confirm = { hour = state.hour; minute = state.minute; chooseTime = false }
        Dialog(onDismissRequest = { chooseTime = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp), contentAlignment = Alignment.Center) {
                val shortWide = configuration.screenWidthDp >= 600 && compactPicker
                Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp,
                    modifier = Modifier.widthIn(max = if (shortWide) 560.dp else 360.dp).fillMaxWidth()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(if (shortWide) 12.dp else 24.dp)) {
                        if (shortWide) Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeInput(state = state, modifier = Modifier.weight(1f))
                            Column {
                                TextButton(onClick = confirm) { Text("确定") }
                                TextButton(onClick = { chooseTime = false }) { Text("取消") }
                            }
                        } else {
                            Text("选择时间", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(20.dp))
                            if (input || compactPicker) TimeInput(state = state)
                            else TimePicker(state = state, layoutType = TimePickerLayoutType.Vertical)
                            if (!compactPicker) TextButton(onClick = { input = !input }) {
                                Icon(if (input) Icons.Rounded.AccessTime else Icons.Rounded.Keyboard, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp)); Text(if (input) "表盘选择" else "键盘输入")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { chooseTime = false }) { Text("取消") }
                                TextButton(onClick = confirm) { Text("确定") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (chooseDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(dateText).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            initialDisplayMode = if (compactPicker) DisplayMode.Input else DisplayMode.Picker)
        LaunchedEffect(compactPicker) { if (compactPicker) state.displayMode = DisplayMode.Input }
        DatePickerDialog(onDismissRequest = { chooseDate = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { dateText = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                chooseDate = false
            }) { Text("确定") } }, dismissButton = { TextButton(onClick = { chooseDate = false }) { Text("取消") } }) {
            DatePicker(state, showModeToggle = !compactPicker)
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false },
        title = { Text("放弃未保存的修改？") }, text = { Text("返回后，这次编辑的内容不会保存。") },
        confirmButton = { TextButton(onClick = { discard = false; onBack() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { discard = false }) { Text("继续编辑") } })
}

@Composable
fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))
}

fun weekdayName(day: DayOfWeek) = listOf("一", "二", "三", "四", "五", "六", "日")[day.value - 1]
fun timeText(hour: Int, minute: Int) = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
fun ruleText(schedule: Schedule): String = when (schedule.rule) {
    RepeatRule.ONCE -> schedule.date.format(DateTimeFormatter.ofPattern("M月d日")) + " · 仅一次"
    RepeatRule.WORKDAY -> "中国大陆工作日"
    RepeatRule.WEEKLY -> if (schedule.weekdays.size == 7) "每天"
        else schedule.weekdays.sortedBy { it.value }.joinToString("  ") { "周${weekdayName(it)}" }
}
