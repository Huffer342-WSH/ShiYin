package com.Huffer342.shiyin

import com.Huffer342.shiyin.ui.audioBitrateText
import com.Huffer342.shiyin.ui.audioFormatText
import com.Huffer342.shiyin.ui.audioSizeText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicLibrarySettingsTest {
    @Test
    fun recordingsAreExcludedByDefault() {
        assertFalse(defaultMediaDirectoryIncluded("Recordings/"))
        assertFalse(defaultMediaDirectoryIncluded("Recordings/Call/"))
        assertTrue(defaultMediaDirectoryIncluded("Music/"))
    }

    @Test
    fun explicitOverrideCanIncludeRecordings() {
        val key = mediaDirectoryKey("external_primary", "Recordings/")
        assertFalse(mediaDirectoryIncluded(key, "Recordings/", emptyMap()))
        assertTrue(mediaDirectoryIncluded(key, "Recordings/", mapOf(key to true)))
    }

    @Test
    fun customDirectoryWinsWhenBothSourcesPointToSameFile() {
        val mediaSong = Song("content://media/external/audio/media/1", "MediaStore", "", 1000)
        val customSong = Song(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Fsong.mp3",
            "Custom",
            "",
            1000
        )
        val storageKey = "external_primary|music/song.mp3"
        val system = MediaStoreScan(listOf(mediaSong), songKeys = mapOf(mediaSong.uri to storageKey))
        val custom = LibraryScan(listOf(customSong), emptyList(), songKeys = mapOf(customSong.uri to storageKey))

        assertEquals(listOf(customSong), mergeLibrarySongs(system, custom))
    }

    @Test
    fun directoryOverridesSurviveStorageRoundTrip() {
        val values = mapOf("external_primary|recordings/" to true, "external_primary|ringtones/" to false)
        assertEquals(values, LibraryCodec.readMediaDirectoryOverrides(LibraryCodec.mediaDirectoryOverrides(values)))
    }

    @Test
    fun audioDetailUnitsAreReadable() {
        assertEquals("未知", audioSizeText(-1))
        assertEquals("512 B", audioSizeText(512))
        assertEquals("1.5 KB", audioSizeText(1536))
        assertEquals("2.0 MB", audioSizeText(2 * 1024 * 1024L))
        assertEquals("未知", audioBitrateText(0))
        assertEquals("128 kbps", audioBitrateText(128_000))
        assertEquals("M4A · audio/mp4", audioFormatText("song.m4a", "audio/mp4"))
        assertEquals("FLAC", audioFormatText("song.flac", ""))
        assertEquals("未知", audioFormatText("song", ""))
    }
}
