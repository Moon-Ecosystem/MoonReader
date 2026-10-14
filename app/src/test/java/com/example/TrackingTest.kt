package com.example

import com.example.data.Chapter
import com.example.data.db.MangaTrackEntity
import com.example.data.storage.NaturalOrderComparator
import com.example.data.tracking.ScoreFormat
import com.example.data.tracking.TrackStatus
import com.example.data.tracking.trackNumbers
import com.example.data.tracking.withRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrackingTest {
    private val today = "2026-10-14"
    private val track = MangaTrackEntity(mangaId = "m", remoteId = 1, title = "T", totalChapters = 10)

    private fun chapter(fileName: String) =
        Chapter("id", "m", fileName, fileName, fileName, NaturalOrderComparator.extractChapterNumber(fileName))

    @Test
    fun `reading starts the entry and sets the start date`() {
        val t = track.copy(status = TrackStatus.PLANNING).withRead(3, null, today)
        assertEquals(3, t.progress)
        assertEquals(TrackStatus.CURRENT, t.status)
        assertEquals(today, t.startedAt)
    }

    @Test
    fun `linking uses the local first and last read dates`() {
        val t = track.withRead(10, null, today = "2026-03-20", startedOn = "2026-01-05")
        assertEquals("2026-01-05", t.startedAt)
        assertEquals("2026-03-20", t.completedAt)
        // A start date already on AniList is kept
        assertEquals("2025-12-01", track.copy(startedAt = "2025-12-01").withRead(3, null, today, "2026-01-05").startedAt)
    }

    @Test
    fun `progress never goes down, so rereads change nothing`() {
        val t = track.copy(status = TrackStatus.CURRENT, progress = 5)
        assertSame(t, t.withRead(2, null, today))
    }

    @Test
    fun `reaching the total completes the entry`() {
        val t = track.copy(status = TrackStatus.CURRENT, progress = 9, startedAt = "2026-01-01").withRead(10, null, today)
        assertEquals(TrackStatus.COMPLETED, t.status)
        assertEquals(today, t.completedAt)
        assertEquals("2026-01-01", t.startedAt)
    }

    @Test
    fun `dropped stays dropped until finished`() {
        assertEquals(TrackStatus.DROPPED, track.copy(status = TrackStatus.DROPPED).withRead(4, null, today).status)
    }

    @Test
    fun `score formats map AniList's raw 0-100 score both ways`() {
        assertEquals("8.5 / 10", ScoreFormat.POINT_10_DECIMAL.label(85))
        assertEquals(85, ScoreFormat.POINT_10_DECIMAL.toRaw(85))
        assertEquals("73 / 100", ScoreFormat.POINT_100.label(73))
        assertEquals("9 / 10", ScoreFormat.POINT_10.label(85))
        assertEquals(80, ScoreFormat.POINT_10.toRaw(8))
        assertEquals("★★★★☆", ScoreFormat.POINT_5.label(80))
        assertEquals(60, ScoreFormat.POINT_5.toRaw(3))
        assertEquals("😊", ScoreFormat.POINT_3.label(85))
        assertEquals(35, ScoreFormat.POINT_3.toRaw(1))
        assertEquals("Not scored", ScoreFormat.POINT_10.label(0))
        // A format's own values survive a round trip
        ScoreFormat.entries.forEach { f -> (0..f.steps).forEach { assertEquals(it, f.toStep(f.toRaw(it))) } }
    }

    @Test
    fun `volume files count as volumes, chapter files as chapters`() {
        assertEquals(null to 3, trackNumbers(chapter("Horimiya - Vol 3.cbz")))
        assertEquals(null to 1, trackNumbers(chapter("Berserk v01 (Digital).cbz")))
        assertEquals(12 to null, trackNumbers(chapter("Chainsaw Man Ch. 12.cbz")))
        assertEquals(5 to null, trackNumbers(chapter("Vinland Saga v01 c005.cbz")))
        assertEquals(7 to null, trackNumbers(chapter("007.cbz")))
    }
}
