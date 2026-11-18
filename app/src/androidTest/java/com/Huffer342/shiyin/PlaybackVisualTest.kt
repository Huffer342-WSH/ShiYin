package com.Huffer342.shiyin

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.geometry.Offset
import androidx.test.platform.app.InstrumentationRegistry
import com.Huffer342.shiyin.ui.*
import com.Huffer342.shiyin.ui.theme.ShiYinTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class PlaybackVisualTest {
    @get:Rule val compose = createComposeRule()

    @Test fun countdownCanBeDisabledAndRestartedWithoutStaleTime() {
        var enabled by mutableStateOf(false)
        var end by mutableLongStateOf(System.currentTimeMillis() - 1000)
        compose.setContent { ShiYinTheme { CountdownRemaining(end, enabled, 20) } }
        compose.onNodeWithText("0 秒").assertExists()
        compose.runOnIdle { end = System.currentTimeMillis() + 20_000; enabled = true }
        compose.onNodeWithText("20 秒").assertExists()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithText("0 秒").assertExists()
        compose.runOnIdle { end = System.currentTimeMillis() + 20_000; enabled = true }
        compose.onNodeWithText("20 秒").assertExists()
    }

    @Test fun lyricsPageReadsSavedTimedTextAndFollowsPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("lyrics", 0)
        val uri = "test:lyrics"
        prefs.edit().putString(uri, "[00:01]first\n[00:02]second\n[00:03]third\n[00:04]fourth").commit()
        var position by mutableLongStateOf(1000)
        try {
            compose.setContent { ShiYinTheme {
                PlaybackVisuals(PlaybackUi(uri = uri, position = position),
                    androidx.compose.ui.Modifier.fillMaxSize())
            } }
            compose.onNodeWithText("歌词").performClick()
            compose.onNodeWithText("first").assertExists()
            compose.runOnIdle { position = 4000 }
            compose.onNodeWithText("first").assertDoesNotExist()
            compose.onNodeWithText("fourth").assertExists()
        } finally { prefs.edit().remove(uri).commit() }
    }

    @Test fun dragUpdatesTimeBeforeRelease() {
        var seek = -1L
        compose.setContent { ShiYinTheme {
            NowPlayingScreen(PlaybackUi(title = "test", uri = "content://test", duration = 120000),
                {}, {}, {}, {}, {}, { seek = it }, {}, {}, {}, {}, {}, {})
        } }
        val slider = compose.onNodeWithTag("playback_seek")
        slider.performTouchInput {
            down(Offset(width * .2f, height / 2f))
            moveTo(Offset(width * .7f, height / 2f))
        }
        compose.onNodeWithTag("seek_time").assert(hasText("0:00").not())
        assertEquals(-1L, seek)
        slider.performTouchInput { up() }
        compose.runOnIdle { assertTrue(seek in 60000..110000) }
    }

    @Test fun themeChangesSurfaceAsWellAsAccent() {
        var palette by mutableStateOf(ThemePalette.FOREST)
        var surface = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { ShiYinTheme(settings = ThemeSettings(palette = palette, mode = ThemeMode.LIGHT)) {
            surface = MaterialTheme.colorScheme.surface
        } }
        val before = surface
        compose.runOnIdle { palette = ThemePalette.CUSTOM }
        compose.waitForIdle()
        assertNotEquals(before, surface)
    }

    @Test fun swipeHasThresholdAndCommitAnimation() {
        var exits = 0
        var progress by mutableFloatStateOf(0f)
        compose.setContent { ShiYinTheme {
            BackgroundTransition({ progress }, false) {
                BackgroundNavigation({ assertEquals(1f, progress); exits++ }, { progress = it }) {
                    NavigationBarItem(true, {}, { Text("歌曲") })
                }
            }
        } }
        compose.onNodeWithTag("background_swipe").performTouchInput {
            down(center); moveBy(Offset(0f, -10f)); up()
        }
        compose.waitForIdle()
        assertEquals(0, exits)
        compose.onNodeWithTag("background_swipe").performTouchInput {
            down(center); moveBy(Offset(0f, -250f)); up()
        }
        compose.waitForIdle()
        assertEquals(1, exits)
    }

    @Test fun waveformReflectsSilentAndLoudHalves() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rate = 16000
        val pcm = ByteBuffer.allocate(rate * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(rate * 2) { i ->
            pcm.putShort(if (i < rate) 0 else (sin(i * 2 * Math.PI * 440 / rate) * 16000).toInt().toShort())
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.capacity()).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
            .put("data".toByteArray()).putInt(pcm.capacity())
        val file = java.io.File(context.cacheDir, "waveform-test.wav")
        try {
            file.outputStream().use { it.write(header.array()); it.write(pcm.array()) }
            val peaks = decodeWaveform(context, android.net.Uri.fromFile(file).toString())
            assertEquals(240, peaks.size)
            assertTrue(peaks.take(100).all { it < .01f })
            assertTrue(peaks.takeLast(100).all { it > .4f })
        } finally { file.delete() }
    }
}
