package com.Huffer342.shiyin

import org.junit.Assert.*
import org.junit.Test

class PlayerFeaturesTest {
    private val first = Song("content://media/external/audio/media/1", "First", "Artist", 1000)
    private val second = Song("content://media/external/audio/media/2", "Second", "Artist", 2000)
    private val third = Song("content://media/external/audio/media/3", "Third", "Artist", 3000)

    @Test fun playlistOperationsKeepOrderAndDoNotDuplicate() {
        val list = MusicPlaylist(name = "Favorites").adding(listOf(first, second, first, third))
        assertEquals(listOf(first, second, third), list.songs)
        assertEquals(listOf(third, first, second), list.moving(2, 0).songs)
        assertEquals(listOf(first, third), list.removing(second.uri).songs)
    }

    @Test fun playlistsSurviveStorageRoundTrip() {
        val lists = listOf(MusicPlaylist(name = "甲", songs = listOf(second, first)), MusicPlaylist(name = "乙"))
        assertEquals(lists, LibraryCodec.readPlaylists(LibraryCodec.playlists(lists)))
    }

    @Test(expected = IllegalArgumentException::class) fun invalidPlaylistNameIsRejected() {
        MusicPlaylist(name = " ")
    }

    @Test(expected = IllegalArgumentException::class) fun invalidMoveIsRejected() {
        MusicPlaylist(name = "List", songs = listOf(first)).moving(0, 2)
    }

    @Test fun localReferencesRejectRemoteAndOtherProviders() {
        assertTrue(isLocalAudioReference(first.uri))
        assertTrue(isLocalAudioReference("content://com.android.externalstorage.documents/tree/primary%3AMusic/document/primary%3AMusic%2Ftest.mp3"))
        assertFalse(isLocalAudioReference("https://example.com/song.mp3"))
        assertFalse(isLocalAudioReference("content://untrusted/tree/music/document/song.mp3"))
        assertFalse(isLocalAudioReference("content://media/external/images/media/1"))
    }

    @Test fun folderStorageRoundTrip() {
        val folders = listOf(WatchedFolder("content://com.android.externalstorage.documents/tree/primary%3AMusic", "Music"))
        assertEquals(folders, LibraryCodec.readFolders(LibraryCodec.folders(folders)))
    }

    @Test fun fadeGainIsBoundedAndLinear() {
        assertEquals(0f, PlaybackTiming.fadeGain(-1, 10), 0f)
        assertEquals(0f, PlaybackTiming.fadeGain(0, 10), 0f)
        assertEquals(.5f, PlaybackTiming.fadeGain(5000, 10), .001f)
        assertEquals(1f, PlaybackTiming.fadeGain(15000, 10), 0f)
        assertEquals(1f, PlaybackTiming.fadeGain(0, 0), 0f)
    }

    @Test fun sleepTimerCancelsAndExpiresAtBoundary() {
        val deadline = PlaybackTiming.sleepDeadline(500, 1)
        assertEquals(60_500, deadline)
        assertFalse(PlaybackTiming.expired(deadline, 60_499))
        assertTrue(PlaybackTiming.expired(deadline, 60_500))
        assertEquals(0, PlaybackTiming.sleepDeadline(500, 0))
        assertFalse(PlaybackTiming.expired(0, 100_000))
    }

    @Test fun oldSchedulesLoadWithoutFadeAndNewSchedulesKeepIt() {
        val old = """[{"id":"old","title":"Music","uri":"content://media/external/audio/media/1","hour":7,"minute":0,"rule":"ONCE","date":"2026-11-17","weekdays":[],"stopMinutes":30,"enabled":false}]"""
        assertEquals(0, ScheduleCodec.decode(old).single().fadeSeconds)
        val updated = ScheduleCodec.decode(old).single().copy(fadeSeconds = 15)
        assertEquals(updated, ScheduleCodec.decode(ScheduleCodec.encode(listOf(updated))).single())
    }

    @Test(expected = IllegalArgumentException::class) fun fadeCannotReachOrExceedAlarmLimit() {
        Schedule(title = "Music", uri = first.uri, hour = 7, minute = 0, stopMinutes = 1, fadeSeconds = 60)
    }

    @Test fun customColorValidationIsStrict() {
        assertEquals(0xFFAABBCC.toInt(), parseThemeColor("#aabbcc"))
        assertNull(parseThemeColor("#FFF"))
        assertNull(parseThemeColor("blue"))
        assertNull(parseThemeColor("#GG0000"))
    }

    @Test fun customAccentsRemainReadableOnLightAndDarkSurfaces() {
        for (seed in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFF00.toInt(), 0xFF0000FF.toInt())) {
            assertTrue(colorContrast(accessibleAccent(seed, false), 0xFFFAF8F3.toInt()) >= 4.5)
            assertTrue(colorContrast(accessibleAccent(seed, true), 0xFF141B17.toInt()) >= 4.5)
        }
    }
}
