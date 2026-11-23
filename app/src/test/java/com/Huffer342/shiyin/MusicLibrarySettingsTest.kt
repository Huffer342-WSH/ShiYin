package com.Huffer342.shiyin

import com.Huffer342.shiyin.ui.audioBitrateText
import com.Huffer342.shiyin.ui.audioFormatText
import com.Huffer342.shiyin.ui.audioSizeText
import com.Huffer342.shiyin.ui.SongGroup
import com.Huffer342.shiyin.ui.SongSort
import com.Huffer342.shiyin.ui.groupSongs
import com.Huffer342.shiyin.ui.sortSongs
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
    fun songsSortByEverySupportedFieldAndDirection() {
        val zulu = Song("content://media/external/audio/media/1", "Zulu", "Beta", 3_000, "b.mp3", "Music/Z/b.mp3")
        val alpha = Song("content://media/external/audio/media/2", "Alpha", "Gamma", 1_000, "c.mp3", "Music/A/c.mp3")
        val middle = Song("content://media/external/audio/media/3", "Middle", "Alpha", 2_000, "a.mp3", "Download/a.mp3")
        val songs = listOf(zulu, alpha, middle)

        assertEquals(listOf(alpha, middle, zulu), sortSongs(songs, SongSort.TITLE, true))
        assertEquals(listOf(middle, zulu, alpha), sortSongs(songs, SongSort.FILE_NAME, true))
        assertEquals(listOf(middle, alpha, zulu), sortSongs(songs, SongSort.FILE_PATH, true))
        assertEquals(listOf(alpha, middle, zulu), sortSongs(songs, SongSort.DURATION, true))
        assertEquals(listOf(middle, zulu, alpha), sortSongs(songs, SongSort.ARTIST, true))
        assertEquals(listOf(zulu, middle, alpha), sortSongs(songs, SongSort.TITLE, false))
    }

    @Test
    fun songsGroupLikeAFileManager() {
        val zulu = Song("content://media/external/audio/media/1", "Zulu", "Beta", 16 * 60_000L, "z.mp3", "Music/Z/z.mp3")
        val alpha = Song("content://media/external/audio/media/2", "Alpha", "Gamma", 30_000, "a.flac", "Music/A/a.flac")
        val middle = Song("content://media/external/audio/media/3", "Middle", "Alpha", 2 * 60_000L, "m.mp3", "Download/m.mp3")
        val songs = listOf(zulu, alpha, middle)

        assertEquals(listOf("A", "M", "Z"), groupSongs(songs, SongGroup.TITLE_INITIAL).map { it.first })
        assertEquals(listOf("Download", "Music/A", "Music/Z"), groupSongs(songs, SongGroup.FOLDER).map { it.first })
        assertEquals(listOf("FLAC", "MP3"), groupSongs(songs, SongGroup.FILE_TYPE).map { it.first })
        assertEquals(listOf("1 分钟以内", "1–5 分钟", "15 分钟以上"),
            groupSongs(songs, SongGroup.DURATION).map { it.first })
        assertEquals(listOf("Alpha", "Beta", "Gamma"), groupSongs(songs, SongGroup.ARTIST).map { it.first })
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
