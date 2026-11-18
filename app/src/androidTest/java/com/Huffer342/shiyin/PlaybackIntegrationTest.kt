package com.Huffer342.shiyin

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PlaybackIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun awaitMain(timeout: Long = 10_000, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            var satisfied = false
            instrumentation.runOnMainSync { satisfied = condition() }
            if (satisfied) return
            Thread.sleep(100)
        }
        fail("Timed out waiting for playback state")
    }

    @Test fun localPlaybackPreviewAndBackgroundAlarm() {
        // Run on a disposable emulator: this test grants this app's audio/notification permissions.
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, MusicLibrary.permission)
        if (Build.VERSION.SDK_INT >= 33) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertTrue("Enable alarms and reminders for ShiYin before this test", ScheduleScheduler.canSchedule(context))
        val previousNotice = AppStore.notice(context)
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "ShiYin-test-${UUID.randomUUID()}.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/ShiYinTest")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        })!!
        var scenario: ActivityScenario<MainActivity>? = null
        var future: ListenableFuture<MediaController>? = null
        var task: Schedule? = null
        try {
            // Two seconds of silent mono PCM; no copyrighted or user-owned audio is used.
            val length = 8000 * 2 * 2
            val wav = ByteBuffer.allocate(44 + length).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + length); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(length)
                put(ByteArray(length))
            }.array()
            context.contentResolver.openOutputStream(uri)!!.use { it.write(wav) }
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
            val song = MusicLibrary.scan(context).first { it.uri == uri.toString() }
            assertTrue(song.duration > 0)
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val holder = AtomicReference<ListenableFuture<MediaController>>()
            instrumentation.runOnMainSync {
                holder.set(MediaController.Builder(context,
                    SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync())
            }
            future = holder.get()
            val controller = future.get(10, TimeUnit.SECONDS)
            instrumentation.runOnMainSync {
                controller.setMediaItem(song.mediaItem())
                controller.prepare()
                controller.play()
            }
            awaitMain { controller.isPlaying && controller.currentPosition > 100 }
            instrumentation.runOnMainSync {
                assertEquals(C.USAGE_MEDIA, controller.audioAttributes.usage)
                controller.repeatMode = Player.REPEAT_MODE_ONE
                controller.addMediaItem(song.mediaItem())
            }
            awaitMain { controller.isPlaying && controller.mediaItemCount == 2 }
            fun setSleep(minutes: Int) {
                val result = AtomicReference<ListenableFuture<SessionResult>>()
                instrumentation.runOnMainSync {
                    result.set(controller.sendCustomCommand(SessionCommand(PlaybackService.COMMAND_SLEEP, Bundle.EMPTY),
                        Bundle().apply { putInt("minutes", minutes) }))
                }
                assertEquals(SessionResult.RESULT_SUCCESS, result.get().get(5, TimeUnit.SECONDS).resultCode)
            }
            setSleep(1)
            awaitMain { controller.sessionExtras.getLong("sleepDeadline") > SystemClock.elapsedRealtime() }
            setSleep(0)
            awaitMain { controller.sessionExtras.getLong("sleepDeadline") == 0L }
            setSleep(1)
            awaitMain(65_000) { controller.mediaItemCount == 0 && !controller.isPlaying }

            instrumentation.runOnMainSync {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_PREVIEW).putExtra("uri", song.uri).putExtra("title", song.title).putExtra("fadeSeconds", 5))
            }
            awaitMain {
                controller.isPlaying && controller.audioAttributes.usage == C.USAGE_ALARM &&
                    controller.repeatMode == Player.REPEAT_MODE_ONE
            }
            instrumentation.runOnMainSync { assertTrue(controller.volume < 1f) }
            awaitMain(7_000) { controller.volume >= .99f }
            awaitMain(35_000) { controller.mediaItemCount == 0 && !controller.isPlaying }

            val nextMinute = ZonedDateTime.now().plusMinutes(1).withSecond(0).withNano(0)
            task = Schedule(title = song.title, uri = song.uri, hour = nextMinute.hour,
                minute = nextMinute.minute, date = nextMinute.toLocalDate(), stopMinutes = 1)
            ScheduleScheduler.save(context, task)
            val registered = synchronized(AppStore.lock) { AppStore.schedules(context).first { it.id == task.id } }
            assertTrue("Alarm must have a future trigger", registered.nextAt > System.currentTimeMillis())
            scenario.moveToState(Lifecycle.State.CREATED)
            awaitMain(75_000) {
                controller.isPlaying && controller.audioAttributes.usage == C.USAGE_ALARM
            }
            val consumed = synchronized(AppStore.lock) { AppStore.schedules(context).first { it.id == task.id } }
            assertFalse(consumed.enabled)
            assertEquals(0, consumed.nextAt)
            instrumentation.runOnMainSync { assertNull(controller.playerError) }
            instrumentation.runOnMainSync { controller.stop() }
            awaitMain { controller.mediaItemCount == 0 && !controller.isPlaying }
        } finally {
            task?.let { ScheduleScheduler.delete(context, it.id) }
            future?.let { pending -> instrumentation.runOnMainSync { MediaController.releaseFuture(pending) } }
            scenario?.close()
            context.stopService(Intent(context, PlaybackService::class.java))
            context.contentResolver.delete(uri, null, null)
            AppStore.notice(context, previousNotice)
        }
    }
}
