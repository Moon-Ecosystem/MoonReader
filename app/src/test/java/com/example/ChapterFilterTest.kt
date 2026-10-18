package com.example

import com.example.data.Chapter
import com.example.data.ChapterFilter
import com.example.ui.visibleChapters
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterFilterTest {
  private fun ch(id: String, read: Boolean = false, page: Int = 0, bookmarked: Boolean = false, time: Long = 0L) =
    Chapter(id, "m", id, "$id.cbz", "$id.cbz", isRead = read, lastReadPage = page, isBookmarked = bookmarked, lastReadTime = time)

  private val chapters = listOf(
    ch("1", read = true, time = 10),
    ch("2", page = 5, bookmarked = true, time = 30),
    ch("3"),
    ch("4", read = true, bookmarked = true, time = 20)
  )

  private fun ids(filter: ChapterFilter, bookmarked: Boolean = false, byLastRead: Boolean = false) =
    visibleChapters(chapters, filter, bookmarked, byLastRead).map { it.id }

  @Test
  fun `status filters split read, started and untouched chapters`() {
    assertEquals(listOf("1", "2", "3", "4"), ids(ChapterFilter.ALL))
    assertEquals(listOf("2", "3"), ids(ChapterFilter.UNREAD)) // started-but-unfinished counts as unread
    assertEquals(listOf("2"), ids(ChapterFilter.IN_PROGRESS))
    assertEquals(listOf("1", "4"), ids(ChapterFilter.READ))
  }

  @Test
  fun `bookmarked only combines with the status filter`() {
    assertEquals(listOf("2", "4"), ids(ChapterFilter.ALL, bookmarked = true))
    assertEquals(listOf("4"), ids(ChapterFilter.READ, bookmarked = true))
  }

  @Test
  fun `last read puts the most recent first and never-read chapters last in their order`() {
    assertEquals(listOf("2", "4", "1", "3"), ids(ChapterFilter.ALL, byLastRead = true))
  }
}
