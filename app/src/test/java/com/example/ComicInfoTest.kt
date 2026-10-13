package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.storage.CbzArchive
import com.example.data.storage.ComicInfoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ComicInfoTest {
  private val xml = """
    <?xml version="1.0" encoding="utf-8"?>
    <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      <Series>Uzumaki</Series>
      <Summary>A town haunted by spirals.</Summary>
      <Writer>Junji Ito</Writer>
      <Penciller>Junji Ito</Penciller>
      <Publisher>VIZ Media</Publisher>
      <Year>2018</Year>
      <Genre>Horror, Seinen</Genre>
      <Tags>horror;Supernatural</Tags>
      <Pages><Page Image="0" Type="FrontCover"/></Pages>
    </ComicInfo>
  """.trimIndent()

  @Test
  fun `parses the fields shown on the manga screen`() {
    val info = ComicInfoParser.parse(xml.byteInputStream())!!
    assertEquals("Uzumaki", info.series)
    assertEquals("A town haunted by spirals.", info.summary)
    assertEquals("Junji Ito", info.writer)
    assertEquals("Junji Ito", info.artist)
    assertEquals("VIZ Media", info.publisher)
    assertEquals(2018, info.year)
    assertEquals(listOf("Horror", "Seinen", "Supernatural"), info.genres) // Genre + Tags, case-insensitive dedupe
  }

  @Test
  fun `malformed or empty ComicInfo yields null`() {
    assertNull(ComicInfoParser.parse("<ComicInfo><Series>oops".byteInputStream()))
    assertNull(ComicInfoParser.parse("<ComicInfo><Pages/></ComicInfo>".byteInputStream()))
  }

  @Test
  fun `archive finds ComicInfo xml next to the pages`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    fun cbz(name: String, withInfo: Boolean) = java.io.File(context.cacheDir, name).also { f ->
      java.util.zip.ZipOutputStream(java.io.FileOutputStream(f)).use { zos ->
        zos.putNextEntry(java.util.zip.ZipEntry("p1.jpg")); zos.write("img".toByteArray()); zos.closeEntry()
        if (withInfo) { zos.putNextEntry(java.util.zip.ZipEntry("comicinfo.XML")); zos.write(xml.toByteArray()); zos.closeEntry() }
      }
    }
    val withInfo = CbzArchive.open(context, cbz("with_info.cbz", true).absolutePath)!!.use { archive ->
      assertEquals(1, archive.pages.size) // ComicInfo.xml is not a page
      archive.openComicInfo()?.use { ComicInfoParser.parse(it) }
    }
    assertEquals("Uzumaki", withInfo!!.series)
    assertNull(CbzArchive.open(context, cbz("no_info.cbz", false).absolutePath)!!.use { it.openComicInfo() })
  }
}
