package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.LibrarySort
import com.example.data.db.AppDatabase
import com.example.data.db.CollectionMangaEntity
import com.example.data.db.MangaEntity
import com.example.data.preferences.PreferencesManager
import com.example.ui.MangaViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h900dp")
class LibraryReorderUiTest {
  @get:Rule val compose = createEmptyComposeRule()

  private fun openMenu() = compose.onNodeWithTag("home_filter_button").performClick()

  private fun awaitHome() = compose.waitUntil(15_000) {
    compose.onAllNodes(hasTestTag("home_filter_button")).fetchSemanticsNodes().isNotEmpty()
  }

  private fun openEditor() {
    val entry = compose.onNodeWithTag("home_reorder_library")
    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("home_reorder_library")).fetchSemanticsNodes().isNotEmpty() }
    entry.performClick()
    compose.waitUntil(15_000) { compose.onAllNodes(hasTestTag("library_reorder_grid")).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun handle(title: String) = compose.onNode(hasContentDescription("Reorder $title"))

  private fun assertEditorOrder(vararg titles: String) {
    val actual = titles.toList().sortedWith(compareBy<String> { handle(it).fetchSemanticsNode().boundsInRoot.top }
      .thenBy { handle(it).fetchSemanticsNode().boundsInRoot.left })
    assertEquals(titles.toList(), actual)
  }

  private fun drag(source: String, destination: String) {
    val grid = compose.onNodeWithTag("library_reorder_grid")
    val origin = grid.fetchSemanticsNode().boundsInRoot.topLeft
    val start = handle(source).fetchSemanticsNode().boundsInRoot.center - origin
    val end = handle(destination).fetchSemanticsNode().boundsInRoot.center - origin
    grid.performTouchInput { swipe(start, end, durationMillis = 500) }
  }

  @Test
  fun `drag saves independently across filters relaunch refresh and replacement`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val root = File(context.cacheDir, "ui_sort_library").apply { mkdirs() }
    val titles = listOf("Alder", "Birch", "Cedar", "Dogwood")
    for ((index, title) in titles.withIndex()) {
      val dir = File(root, title).apply { mkdirs() }
      val chapter = File(dir, "Chapter 01.cbz")
      ZipOutputStream(chapter.outputStream()).use { }
      check(chapter.setLastModified(1_600_000_000_000L + index * 60_000L))
    }
    PreferencesManager(context).apply {
      setFolderUri(root.absolutePath)
      setLibrarySort(LibrarySort.TITLE, true)
    }
    val collection = runBlocking {
      val db = AppDatabase.getDatabase(context)
      val id = db.collectionDao().create("Test Collection")
      for (title in listOf("Alder", "Cedar")) {
        val path = File(root, title).absolutePath
        db.mangaDao().insertOrUpdateManga(MangaEntity(path, title, path, isFavorite = true, totalChapters = 1))
        db.collectionDao().addMember(CollectionMangaEntity(id, path))
      }
      id
    }

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      openMenu()
      openEditor()
      assertEditorOrder("Alder", "Birch", "Cedar", "Dogwood")
      drag("Alder", "Dogwood")
      assertEditorOrder("Birch", "Cedar", "Dogwood", "Alder")
      compose.onNodeWithTag("library_reorder_save").assertIsEnabled().performClick()
      awaitHome()
      assertEquals(LibrarySort.CUSTOM, PreferencesManager(context).librarySort.value)

      lateinit var viewModel: MangaViewModel
      scenario.onActivity { viewModel = ViewModelProvider(it)[MangaViewModel::class.java] }
      assertEquals(listOf("Cedar", "Alder"), viewModel.homeLibrary.value[collection]?.map { it.title })
      openMenu()
      compose.onNodeWithTag("home_filter_favorites").performClick()
      compose.onNodeWithTag("home_reorder_library").assertDoesNotExist()
      assertEquals(listOf("Cedar", "Alder"), viewModel.homeLibrary.value[PreferencesManager.ALL_COLLECTIONS]?.map { it.title })
      compose.onNodeWithTag("home_filter_favorites").performClick()
      compose.onNodeWithTag("home_sort_ARCHIVE_MODIFIED").performClick()
      openEditor()
      assertEditorOrder("Dogwood", "Cedar", "Birch", "Alder")
      drag("Dogwood", "Alder")
      compose.onNodeWithTag("library_reorder_save").performClick()
      compose.onNodeWithText("Replace custom order?").assertExists()
      compose.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
      compose.onNodeWithTag("library_reorder_cancel").performClick()
      assertEquals(LibrarySort.ARCHIVE_MODIFIED, PreferencesManager(context).librarySort.value)

      openMenu()
      compose.onNodeWithTag("home_sort_CUSTOM").performClick()
      openEditor()
      assertEditorOrder("Birch", "Cedar", "Dogwood", "Alder")
      compose.onNodeWithTag("library_reorder_cancel").performClick()
    }

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      openMenu()
      openEditor()
      assertEditorOrder("Birch", "Cedar", "Dogwood", "Alder")
      compose.onNodeWithTag("library_reorder_cancel").performClick()

      check(File(root, "Alder/Chapter 01.cbz").setLastModified(1_700_000_000_000L))
      scenario.onActivity { ViewModelProvider(it)[MangaViewModel::class.java].scanLibrary() }
      openMenu()
      compose.onNodeWithTag("home_sort_ARCHIVE_MODIFIED").performClick()
      openEditor()
      assertEditorOrder("Alder", "Dogwood", "Cedar", "Birch")
      drag("Birch", "Alder")
      compose.onNodeWithTag("library_reorder_save").performClick()
      compose.onNodeWithText("Replace").performClick()
      awaitHome()
      openMenu()
      openEditor()
      assertEditorOrder("Birch", "Alder", "Dogwood", "Cedar")
      compose.onNodeWithTag("library_reorder_cancel").performClick()

      for (i in 1..30) {
        val dir = File(root, "New %02d".format(i)).apply { mkdirs() }
        ZipOutputStream(File(dir, "Chapter 01.cbz").outputStream()).use { }
      }
      scenario.onActivity { ViewModelProvider(it)[MangaViewModel::class.java].scanLibrary() }
      openMenu()
      openEditor()
      assertEditorOrder("Birch", "Alder", "Dogwood", "Cedar", "New 01", "New 02")

      val grid = compose.onNodeWithTag("library_reorder_grid")
      val bounds = grid.fetchSemanticsNode().boundsInRoot
      val start = handle("Birch").fetchSemanticsNode().boundsInRoot.center - bounds.topLeft
      handle("New 30").assertDoesNotExist()
      compose.mainClock.autoAdvance = false
      try {
        grid.performTouchInput {
          down(start)
          moveTo(Offset(start.x, bounds.height - 4f))
        }
        compose.mainClock.advanceTimeBy(4_000)
        grid.performTouchInput { up() }
      } finally {
        compose.mainClock.autoAdvance = true
      }
      handle("New 30").assertExists()
    }
  }
}
