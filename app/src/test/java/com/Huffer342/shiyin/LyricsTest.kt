package com.Huffer342.shiyin
import org.junit.Assert.*
import org.junit.Test

class LyricsTest {
    @Test fun timestampsFractionsOffsetAndMultipleTags() {
        val lines = parseLrc("[ar:test]\n[offset:-500]\n[00:02.50][00:04.005]hello\n[00:01]first\n[00:99]invalid")
        assertEquals(listOf(500L, 2000L, 3505L), lines.map { it.timeMs })
        assertEquals(listOf("first", "hello", "hello"), lines.map { it.text })
    }
    @Test fun plainTextIsNotPretendedToBeTimedLyrics() {
        assertTrue(parseLrc("some words").isEmpty())
    }
}
