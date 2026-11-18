package com.Huffer342.shiyin.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun BackgroundNavigation(onBackground: () -> Unit, onProgress: (Float) -> Unit = {}, content: @Composable RowScope.() -> Unit) {
    val scope = rememberCoroutineScope()
    val action by rememberUpdatedState(onBackground)
    val progress by rememberUpdatedState(onProgress)
    val threshold = with(LocalDensity.current) { 48.dp.toPx() }
    var drag by remember { mutableFloatStateOf(0f) }
    var releasing by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }
    Box {
        if (drag < 0 || releasing) Surface(Modifier.align(Alignment.TopCenter).offset(y = (-40).dp),
            shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(if (-drag >= threshold || releasing) "松手进入后台" else "继续上滑进入后台",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        NavigationBar(modifier = Modifier.testTag("background_swipe")
            .semantics { customActions = listOf(CustomAccessibilityAction("进入后台运行") { action(); true }) }
            .pointerInput(threshold) {
                detectVerticalDragGestures(
                    onDragStart = { if (!releasing) drag = 0f },
                    onDragCancel = {
                        drag = 0f
                        scope.launch { settle.animateTo(0f, tween(220)) { progress(value) } }
                    },
                    onDragEnd = {
                        if (!releasing) {
                            val accepted = -drag >= threshold
                            val from = (-drag / (threshold * 4)).coerceIn(0f, .65f)
                            releasing = true
                            scope.launch {
                                settle.snapTo(from)
                                settle.animateTo(if (accepted) 1f else 0f, tween(320)) { progress(value) }
                                if (accepted) action()
                                settle.animateTo(0f, tween(220)) { progress(value) }
                                drag = 0f
                                releasing = false
                            }
                        }
                    },
                    onVerticalDrag = { change, amount ->
                        if (!releasing) {
                            change.consume(); drag = (drag + amount).coerceIn(-threshold * 2.5f, 0f)
                            progress((-drag / (threshold * 4)).coerceIn(0f, .65f))
                        }
                    })
            }, content = content)
    }
}
