package com.Huffer342.shiyin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun BackgroundTransition(progress: () -> Float, preparing: Boolean, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.align(Alignment.Center).graphicsLayer { alpha = progress() },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Bedtime, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(if (preparing) "正在准备后台运行…" else "后台运行",
                color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxSize().testTag("background_page").graphicsLayer {
            val fraction = progress().coerceIn(0f, 1f)
            translationY = -size.height * fraction
            scaleX = 1f - fraction * .08f
            scaleY = 1f - fraction * .08f
            shape = RoundedCornerShape((fraction * 24).dp)
            clip = fraction > 0
        }, content = content)
    }
}
