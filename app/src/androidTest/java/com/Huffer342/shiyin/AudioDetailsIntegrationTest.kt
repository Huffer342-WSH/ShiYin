package com.Huffer342.shiyin

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class AudioDetailsIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun readsMediaStoreFileDetails() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, MusicLibrary.permission)
        val name = "ShiYin-details-${UUID.randomUUID()}.wav"
        val uri = context.contentResolver.insert(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/ShiYinDetails")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        )!!
        try {
            val dataSize = 8000 * 2
            val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000)
                putShort(2); putShort(16); put("data".toByteArray()); putInt(dataSize)
                put(ByteArray(dataSize))
            }.array()
            context.contentResolver.openOutputStream(uri)!!.use { it.write(wav) }
            context.contentResolver.update(uri, ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }, null, null)
            var song: Song? = null
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (song == null && SystemClock.elapsedRealtime() < deadline) {
                song = MusicLibrary.scan(context).find { it.uri == uri.toString() }
                if (song == null) Thread.sleep(100)
            }
            val details = MusicLibrary.audioDetails(context, requireNotNull(song))
            assertEquals("系统音乐库", details.source)
            assertEquals(name, details.fileName)
            assertEquals("Music/ShiYinDetails/$name", details.location)
            assertTrue(details.mimeType == "audio/wav" || details.mimeType == "audio/x-wav")
            assertTrue(details.sizeBytes >= wav.size.toLong())
            val intent = MusicLibrary.fileManagerIntent(details)
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
            @Suppress("DEPRECATION")
            val initialUri = requireNotNull(intent.getParcelableExtra<Uri>(DocumentsContract.EXTRA_INITIAL_URI))
            assertEquals("com.android.externalstorage.documents", initialUri.authority)
            assertEquals("primary:Music/ShiYinDetails", DocumentsContract.getDocumentId(initialUri))
        } finally {
            context.contentResolver.delete(uri, null, null)
        }
    }
}
