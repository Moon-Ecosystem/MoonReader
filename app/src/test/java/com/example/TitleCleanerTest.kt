package com.example

import com.example.data.storage.TitleCleaner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TitleCleanerTest {
    @Test
    fun `strips release tags, group names and numbering`() {
        assertEquals(
            "Frankenstein - Junji Ito Story Collection",
            TitleCleaner.clean("Frankenstein - Junji Ito Story Collection (2018) (Digital) (1r0n).cbz")
        )
        assertEquals("Vinland Saga", TitleCleaner.clean("[Group] Vinland Saga v01 (2013) (Digital).cbz"))
        assertEquals("Horimiya", TitleCleaner.clean("Horimiya - Vol 1.cbz"))
        assertEquals("Chainsaw Man", TitleCleaner.clean("Chainsaw_Man_c097.cbz"))
        assertEquals("Berserk", TitleCleaner.clean("Berserk.v01.cbz"))
        assertEquals("One Piece", TitleCleaner.clean("One Piece - Chapter 1100"))
    }

    @Test
    fun `leaves numbers that are part of the title`() {
        assertEquals("Mob Psycho 100", TitleCleaner.clean("Mob Psycho 100"))
        assertEquals("Kaiju No. 8", TitleCleaner.clean("Kaiju No. 8 (2020)"))
        assertEquals("20th Century Boys", TitleCleaner.clean("20th Century Boys"))
        assertEquals("Solo Leveling", TitleCleaner.clean("Solo Leveling"))
    }

    @Test
    fun `falls back to the raw name when cleaning removes everything`() {
        assertEquals("(2018)", TitleCleaner.clean("(2018).cbz"))
    }

    @Test
    fun `reads a bracketed year`() {
        assertEquals(2018, TitleCleaner.year("Frankenstein (2018) (Digital)"))
        assertNull(TitleCleaner.year("Mob Psycho 100"))
    }
}
