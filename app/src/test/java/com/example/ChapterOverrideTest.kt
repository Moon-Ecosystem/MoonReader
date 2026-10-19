package com.example

import com.example.data.Chapter
import com.example.data.db.ChapterOverrideEntity
import com.example.data.db.ChapterProgressEntity
import com.example.data.repository.mergeChapters
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterOverrideTest {
  private fun ch(id: String, title: String = id) = Chapter(id, "m", title, "$id.cbz", "$id.cbz")

  private val chapters = listOf(ch("1", "Chapter 1"), ch("2", "Chapter 2"), ch("3", "Chapter 3"))

  @Test
  fun `without changes the scanned chapters are kept as they are`() {
    assertEquals(chapters, mergeChapters(chapters, emptyList(), emptyList()))
  }

  @Test
  fun `a renamed chapter shows the new name and keeps its file name`() {
    val merged = mergeChapters(chapters, emptyList(), listOf(ChapterOverrideEntity("2", "m", customTitle = "The Duel")))

    assertEquals(listOf("Chapter 1", "The Duel", "Chapter 3"), merged.map { it.title })
    assertEquals("2.cbz", merged[1].fileName)
  }

  @Test
  fun `a hidden chapter is left out of the list`() {
    val merged = mergeChapters(chapters, emptyList(), listOf(ChapterOverrideEntity("2", "m", isHidden = true)))

    assertEquals(listOf("1", "3"), merged.map { it.id })
  }

  @Test
  fun `a removed chapter can be listed again and flagged so it can be restored`() {
    val overrides = listOf(ChapterOverrideEntity("2", "m", isHidden = true))

    val all = mergeChapters(chapters, emptyList(), overrides, includeRemoved = true)

    assertEquals(listOf("1", "2", "3"), all.map { it.id })
    assertEquals(true, all.single { it.id == "2" }.isRemoved)
    assertEquals(false, all.single { it.id == "1" }.isRemoved)
  }

  @Test
  fun `progress and a rename are applied to the same chapter`() {
    val merged = mergeChapters(
      chapters,
      listOf(ChapterProgressEntity("2", "m", "Chapter 2", lastReadPage = 4, totalPages = 20, isRead = true, isBookmarked = true)),
      listOf(ChapterOverrideEntity("2", "m", customTitle = "The Duel"))
    )

    val chapter = merged.single { it.id == "2" }
    assertEquals("The Duel", chapter.title)
    assertEquals(4, chapter.lastReadPage)
    assertEquals(20, chapter.totalPages)
    assertEquals(true, chapter.isRead)
    assertEquals(true, chapter.isBookmarked)
  }
}
