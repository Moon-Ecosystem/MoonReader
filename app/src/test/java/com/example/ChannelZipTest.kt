package com.example

import com.example.data.storage.ChannelZip
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ChannelZipTest {
  private fun readAll(file: File): Map<String, ByteArray> =
    RandomAccessFile(file, "r").use { raf ->
      ChannelZip(raf.channel).use { zip ->
        zip.entries.filter { !it.isDirectory }.associate { e -> e.name to zip.open(e).use { it.readBytes() } }
      }
    }

  private fun readWithJdk(file: File): Map<String, ByteArray> =
    ZipFile(file).use { zip ->
      zip.entries().asSequence().filter { !it.isDirectory }.associate { e -> e.name to zip.getInputStream(e).use { it.readBytes() } }
    }

  @Test
  fun `stored and deflated entries match java util zip byte for byte`() {
    val file = File.createTempFile("channelzip", ".cbz").apply { deleteOnExit() }
    val rnd = Random(7)
    ZipOutputStream(FileOutputStream(file)).use { zos ->
      zos.putNextEntry(ZipEntry("folder/"))
      zos.closeEntry()
      for (i in 1..20) {
        val data = ByteArray(1000 + rnd.nextInt(200_000)).also { b -> if (i % 2 == 0) rnd.nextBytes(b) else b.fill((i % 7).toByte()) }
        if (i % 3 == 0) {
          // STORED needs size and CRC up front
          val crc = java.util.zip.CRC32().apply { update(data) }
          zos.putNextEntry(ZipEntry("folder/p$i.png").apply {
            method = ZipEntry.STORED; size = data.size.toLong(); compressedSize = data.size.toLong(); this.crc = crc.value
          })
        } else {
          zos.putNextEntry(ZipEntry("folder/p$i.jpg"))
        }
        zos.write(data)
        zos.closeEntry()
      }
      zos.putNextEntry(ZipEntry("Ünïcødé ページ.webp"))
      zos.write("unicode".toByteArray())
      zos.closeEntry()
    }

    val expected = readWithJdk(file)
    val actual = readAll(file)
    assertEquals(expected.keys, actual.keys)
    expected.forEach { (name, bytes) -> assertArrayEquals(name, bytes, actual[name]) }
  }

  @Test
  fun `zip64 archives with more than 65535 entries are read`() {
    val file = File.createTempFile("channelzip64", ".zip").apply { deleteOnExit() }
    val count = 65_540
    ZipOutputStream(FileOutputStream(file)).use { zos ->
      for (i in 0 until count) {
        zos.putNextEntry(ZipEntry("p$i.jpg"))
        zos.write(i.toString().toByteArray())
        zos.closeEntry()
      }
    }
    RandomAccessFile(file, "r").use { raf ->
      ChannelZip(raf.channel).use { zip ->
        assertEquals(count, zip.entries.size)
        val last = zip.entries.last()
        assertEquals("p${count - 1}.jpg", last.name)
        assertEquals("${count - 1}", zip.open(last).use { String(it.readBytes()) })
      }
    }
  }
}
