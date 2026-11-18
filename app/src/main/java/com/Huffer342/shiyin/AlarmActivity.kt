package com.Huffer342.shiyin

import android.content.Intent
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import kotlinx.coroutines.delay

class AlarmActivity : ComponentActivity() {
    private var token by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.data?.takeIf { it.scheme == "shiyin" && it.host == "ring" }?.lastPathSegment
            ?: intent.getStringExtra("token")
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else { @Suppress("DEPRECATION") window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON) }
        setContent {
            val alarm by AlarmPresentation.current.collectAsState()
            LaunchedEffect(alarm?.token, token) {
                if (token == null || alarm?.token != token) finish()
            }
            ShiYinTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(32.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        val pulse by rememberInfiniteTransition(label = "ring").animateFloat(
                            0.85f, 1.15f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "pulse")
                        Box(Modifier.size(120.dp).scale(pulse).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center) { Text("♪", style = MaterialTheme.typography.displayLarge) }
                        Spacer(Modifier.height(40.dp))
                        Text(alarm?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                        LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }
                        Text("剩余 ${((alarm?.deadline ?: now) - now).coerceAtLeast(0) / 1000} 秒")
                        Spacer(Modifier.height(32.dp))
                        Button(onClick = {
                            startService(Intent(this@AlarmActivity, PlaybackService::class.java)
                                .setAction(AlarmPresentation.STOP).putExtra("token", token))
                        }, modifier = Modifier.fillMaxWidth()) { Text("停止本次闹钟") }
                        TextButton(onClick = { finish() }) { Text("收起，继续播放") }
                    }
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); token = intent.data?.takeIf { it.scheme == "shiyin" && it.host == "ring" }?.lastPathSegment
            ?: intent.getStringExtra("token") }
}
