package com.example

import com.example.data.LibrarySort
import com.example.data.Manga
import com.example.ui.sortLibrary
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortTest {
  private fun manga(title: String, lastRead: Long = 0L, chapters: Int = 1, read: Int = 0) =
    Manga(id = title, title = title, folderUriString = title, chapterCount = chapters, lastReadTime = lastRead, readChapterCount = read)

  private val library = listOf(
    manga("uzumaki", lastRead = 100, chapters = 20, read = 20),
    manga("Gyo", lastRead = 300, chapters = 2, read = 1),
    manga("Tomie", chapters = 5),
    manga("Remina", chapters = 1),
  )

  private fun titles(sort: LibrarySort, ascending: Boolean) = sortLibrary(library, sort, ascending).map { it.title }

  @Test
  fun `last read puts most recent first and never-read manga last by title`() {
    assertEquals(listOf("Gyo", "uzumaki", "Remina", "Tomie"), titles(LibrarySort.LAST_READ, ascending = false))
  }

  @Test
  fun `title sort ignores case and flips with direction`() {
    assertEquals(listOf("Gyo", "Remina", "Tomie", "uzumaki"), titles(LibrarySort.TITLE, ascending = true))
    assertEquals(listOf("uzumaki", "Tomie", "Remina", "Gyo"), titles(LibrarySort.TITLE, ascending = false))
  }

  @Test
  fun `chapter and unread counts sort largest first by default`() {
    assertEquals(listOf("uzumaki", "Tomie", "Gyo", "Remina"), titles(LibrarySort.TOTAL_CHAPTERS, ascending = false))
    // unread: Tomie 5, Gyo 1, Remina 1 (tie -> title), uzumaki 0
    assertEquals(listOf("Tomie", "Gyo", "Remina", "uzumaki"), titles(LibrarySort.UNREAD, ascending = false))
  }
}
