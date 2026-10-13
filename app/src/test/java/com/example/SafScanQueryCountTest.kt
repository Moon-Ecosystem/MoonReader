package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.MangaScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory documents tree: lib/Manga N/Chapter M.cbz. Counts every query, since each one is a Binder IPC on device. */
class FakeDocsProvider : ContentProvider() {
  companion object {
    const val AUTHORITY = "com.example.fakedocs"
    const val MANGA = 20
    const val CHAPTERS = 30
    var queries = 0
    // docId -> (name, mime)
    val docs = HashMap<String, Pair<String, String>>()
    val children = HashMap<String, List<String>>()

    fun build() {
      docs.clear(); children.clear(); queries = 0
      docs["lib"] = "lib" to Document.MIME_TYPE_DIR
      children["lib"] = (1..MANGA).map { m ->
        val dir = "lib/m$m"
        docs[dir] = "Manga $m" to Document.MIME_TYPE_DIR
        children[dir] = (1..CHAPTERS).map { c ->
          "$dir/c$c".also { docs[it] = "Chapter $c.cbz" to "application/zip" }
        }
        dir
      }
    }
  }

  override fun onCreate() = true

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
    queries++
    val cols = projection ?: arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
    val cursor = MatrixCursor(cols)
    val docId = DocumentsContract.getDocumentId(uri)
    val ids = if (uri.lastPathSegment == "children") children[docId].orEmpty() else listOf(docId)
    for (id in ids) {
      val (name, mime) = docs.getValue(id)
      cursor.addRow(cols.map { col ->
        when (col) {
          Document.COLUMN_DOCUMENT_ID -> id
          Document.COLUMN_DISPLAY_NAME -> name
          Document.COLUMN_MIME_TYPE -> mime
          Document.COLUMN_FLAGS -> 0
          else -> null
        }
      })
    }
    return cursor
  }

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafScanQueryCountTest {
  @Test
  fun `saf scan issues about one query per folder`() {
    FakeDocsProvider.build()
    Robolectric.buildContentProvider(FakeDocsProvider::class.java)
      .create(ProviderInfo().apply { authority = FakeDocsProvider.AUTHORITY })
    val context = ApplicationProvider.getApplicationContext<Context>()
    val treeUri = DocumentsContract.buildTreeDocumentUri(FakeDocsProvider.AUTHORITY, "lib")

    val result = MangaScanner.scanFolder(context, treeUri.toString())
    println("SAF_QUERIES=${FakeDocsProvider.queries} for ${FakeDocsProvider.MANGA} manga x ${FakeDocsProvider.CHAPTERS} chapters")

    assertEquals(FakeDocsProvider.MANGA, result.mangaList.size)
    assertEquals(FakeDocsProvider.CHAPTERS, result.chaptersByMangaId.values.first().size)
    // root listing + one listing per manga folder, plus a couple for the root DocumentFile name
    assertTrue("too many provider queries: ${FakeDocsProvider.queries}", FakeDocsProvider.queries <= FakeDocsProvider.MANGA + 3)
  }
}
