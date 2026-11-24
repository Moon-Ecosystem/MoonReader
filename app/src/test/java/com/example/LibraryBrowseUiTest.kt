package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.LibraryGridLayout
import com.example.data.db.AppDatabase
import com.example.data.db.CollectionMangaEntity
import com.example.data.preferences.PreferencesManager
import com.example.ui.MangaViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w420dp-h900dp", instrumentedPackages = ["com.example.ui.screens"])
class LibraryBrowseUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun waitFor(tag: String) = compose.waitUntil(15_000) {
        compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    }

    private fun chooseGrid(layout: LibraryGridLayout) {
        compose.onNodeWithTag("home_filter_button").performClick()
        compose.onNodeWithTag("home_grid_layout").performClick()
        compose.onNodeWithTag("home_grid_layout_${layout.name}").performClick()
    }

    private fun assertRow(tags: List<String>, columns: Int) {
        val top = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(top.take(columns).all { it == top[0] })
        assertTrue(top[columns] > top[0])
    }

    @Test
    fun `reading and tab navigation retain the viewport and layout persists on reopening`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "browse_library").apply { mkdirs() }
        val page = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jOioAAAAASUVORK5CYII=")
        val books = (1..36).associateWith { index ->
            val folder = File(root, "Book %02d".format(index)).apply { mkdirs() }
            ZipOutputStream(File(folder, "Chapter 01.cbz").outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("001.png"))
                zip.write(page)
                zip.closeEntry()
            }
            folder.absolutePath
        }
        fun card(index: Int) = "manga_card_${books.getValue(index)}"
        val prefs = PreferencesManager(context).apply { setFolderUri(root.absolutePath) }
        assertEquals(LibraryGridLayout.AUTOMATIC, prefs.libraryGridLayout.value)
        val collection = runBlocking {
            val dao = AppDatabase.getDatabase(context).collectionDao()
            dao.create("Test shelf").also { id -> books.values.forEach { dao.addMember(CollectionMangaEntity(id, it)) } }
        }

        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            lateinit var viewModel: MangaViewModel
            activity.onActivity { viewModel = ViewModelProvider(it)[MangaViewModel::class.java] }
            waitFor(card(1))
            assertRow((1..4).map(::card), columns = 3)
            compose.onNodeWithTag("manga_grid").performScrollToIndex(15)
            compose.onNodeWithTag(card(16)).performClick()
            waitFor("primary_read_button")
            compose.waitUntil(15_000) {
                compose.waitForIdle()
                viewModel.currentChapters.value.isNotEmpty()
            }
            compose.onNodeWithTag("primary_read_button").performClick()
            waitFor("reader_screen_container")
            compose.waitUntil(15_000) {
                compose.waitForIdle()
                viewModel.readerPages.value.isNotEmpty()
            }
            activity.onActivity { viewModel.onPageChanged(0) }
            compose.waitUntil(15_000) {
                compose.waitForIdle()
                viewModel.homeLibrary.value[PreferencesManager.ALL_COLLECTIONS]?.firstOrNull()?.id == books[16]
            }
            activity.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            waitFor("chapter_list_back_button")
            compose.onNodeWithTag("chapter_list_back_button").performClick()
            waitFor(card(18))
            compose.onNodeWithTag(card(16)).assertIsNotDisplayed()

            compose.onNodeWithTag("collection_tab_$collection").performClick()
            waitFor(card(16))
            compose.onNodeWithTag("manga_grid").performScrollToIndex(24)
            waitFor(card(25))
            compose.onNodeWithTag("collection_tab_all").performClick()
            waitFor(card(18))
            compose.onNodeWithTag("collection_tab_$collection").performClick()
            waitFor(card(25))
            compose.onNodeWithTag("collection_tab_all").performClick()
            waitFor(card(18))

            chooseGrid(LibraryGridLayout.FOUR_COLUMNS)
            compose.onNodeWithTag(card(18)).assertIsDisplayed()
            compose.onNodeWithTag("manga_grid").performScrollToIndex(0)
            val firstBooks = listOf(16, 1, 2, 3, 4, 5)
            assertRow(firstBooks.take(5).map(::card), columns = 4)
            chooseGrid(LibraryGridLayout.FIVE_COLUMNS)
            assertRow(firstBooks.map(::card), columns = 5)
            compose.onNodeWithTag("manga_grid").performScrollToIndex(15)

            compose.onNodeWithTag("home_filter_button").performClick()
            waitFor("home_reorder_library")
            compose.onNodeWithTag("home_reorder_library").performClick()
            waitFor("library_reorder_grid")
            val handleTags = firstBooks.map { "library_reorder_handle_${books.getValue(it)}" }
            assertRow(handleTags, columns = 5)
            val editorGrid = compose.onNodeWithTag("library_reorder_grid")
            val editorOrigin = editorGrid.fetchSemanticsNode().boundsInRoot.topLeft
            val from = compose.onNodeWithTag(handleTags.first()).fetchSemanticsNode().boundsInRoot.center - editorOrigin
            val to = compose.onNodeWithTag(handleTags.last()).fetchSemanticsNode().boundsInRoot.center - editorOrigin
            editorGrid.performTouchInput { swipe(from, to, durationMillis = 500) }
            val moved = listOf(handleTags[1], handleTags[2], handleTags[3], handleTags[4], handleTags[5], handleTags[0])
            assertRow(moved, columns = 5)
            compose.onNodeWithTag("library_reorder_cancel").performClick()
            waitFor(card(18))
            compose.onNodeWithTag("manga_grid").performScrollToIndex(25)
            compose.onNodeWithTag(card(1)).assertIsNotDisplayed()
        }

        assertEquals(LibraryGridLayout.FIVE_COLUMNS, PreferencesManager(context).libraryGridLayout.value)
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor(card(1))
            assertRow(listOf(16, 1, 2, 3, 4, 5).map(::card), columns = 5)
        }
    }
}
