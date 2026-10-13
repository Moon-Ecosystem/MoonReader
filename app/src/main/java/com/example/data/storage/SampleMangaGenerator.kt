package com.example.data.storage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleMangaGenerator {
    private const val TAG = "SampleMangaGenerator"

    fun getSampleRootFolder(context: Context): File {
        val dir = File(context.filesDir, "sample_manga")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Checks if sample manga already exists, and creates it if not.
     * Returns the folder path.
     */
    fun ensureSampleMangaExists(context: Context): String {
        val rootDir = getSampleRootFolder(context)
        val manga1 = File(rootDir, "Cyber Ronin 2099")
        val manga2 = File(rootDir, "Whispers of the Forest")

        if (manga1.exists() && manga2.exists()) {
            val ch1 = manga1.listFiles()?.filter { it.name.endsWith(".cbz") }
            if (!ch1.isNullOrEmpty()) {
                return rootDir.absolutePath
            }
        }

        generateSampleManga(context)
        return rootDir.absolutePath
    }

    fun generateSampleManga(context: Context) {
        val rootDir = getSampleRootFolder(context)

        // Manga 1: Cyber Ronin 2099
        val roninDir = File(rootDir, "Cyber Ronin 2099")
        roninDir.mkdirs()
        createCyberRoninChapter(roninDir, 1, "Awakening in Sector 7", 5)
        createCyberRoninChapter(roninDir, 2, "Neon Infiltration", 5)
        createCyberRoninChapter(roninDir, 3, "The Core Protocol", 5)

        // Manga 2: Whispers of the Forest
        val forestDir = File(rootDir, "Whispers of the Forest")
        forestDir.mkdirs()
        createForestChapter(forestDir, 1, "The Moss Shrine", 4)
        createForestChapter(forestDir, 2, "Canopy Lanterns", 4)
    }

    private fun createCyberRoninChapter(folder: File, chNum: Int, title: String, pages: Int) {
        val cbzFile = File(folder, String.format(Locale.US, "Ch. %02d - %s.cbz", chNum, title))
        if (cbzFile.exists() && cbzFile.length() > 0) return

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(cbzFile))).use { zos ->
                for (p in 1..pages) {
                    val bitmap = drawCyberRoninPage(chNum, title, p, pages)
                    val entryName = String.format(Locale.US, "page_%03d.png", p)
                    zos.putNextEntry(ZipEntry(entryName))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, zos)
                    zos.closeEntry()
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample CBZ: ${cbzFile.name}", e)
        }
    }

    private fun createForestChapter(folder: File, chNum: Int, title: String, pages: Int) {
        val cbzFile = File(folder, String.format(Locale.US, "Ch. %02d - %s.cbz", chNum, title))
        if (cbzFile.exists() && cbzFile.length() > 0) return

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(cbzFile))).use { zos ->
                for (p in 1..pages) {
                    val bitmap = drawForestPage(chNum, title, p, pages)
                    val entryName = String.format(Locale.US, "page_%03d.png", p)
                    zos.putNextEntry(ZipEntry(entryName))
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, zos)
                    zos.closeEntry()
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sample CBZ: ${cbzFile.name}", e)
        }
    }

    private fun drawCyberRoninPage(chNum: Int, chTitle: String, page: Int, totalPages: Int): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Manga dark paper background
        canvas.drawColor(Color.rgb(18, 18, 24))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (page == 1) {
            // Chapter Splash Cover
            paint.color = Color.rgb(30, 20, 45)
            canvas.drawRect(30f, 30f, width - 30f, height - 30f, paint)

            // Speed lines in background
            paint.color = Color.argb(40, 255, 60, 100)
            paint.strokeWidth = 2f
            for (i in 0 until 40) {
                val angle = (i * 9).toFloat()
                canvas.drawLine(width / 2f, 400f, (width / 2f) + (Math.cos(Math.toRadians(angle.toDouble())) * 600).toFloat(), 400f + (Math.sin(Math.toRadians(angle.toDouble())) * 600).toFloat(), paint)
            }

            // Title block
            paint.color = Color.rgb(255, 75, 120)
            paint.textSize = 28f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("CYBER RONIN", 60f, 120f, paint)

            paint.color = Color.WHITE
            paint.textSize = 64f
            canvas.drawText("2099", 60f, 190f, paint)

            // Decorative kanji / subtext
            paint.color = Color.rgb(180, 180, 200)
            paint.textSize = 22f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("サイバー・浪人・弐千九十九", 60f, 230f, paint)

            // Stylized Katana Silhouette in center
            paint.color = Color.rgb(120, 220, 255)
            paint.strokeWidth = 6f
            canvas.drawLine(180f, 750f, 620f, 350f, paint)
            paint.color = Color.WHITE
            paint.strokeWidth = 2f
            canvas.drawLine(180f, 750f, 620f, 350f, paint)

            // Chapter banner
            val bannerRect = RectF(60f, 850f, width - 60f, 1020f)
            paint.color = Color.rgb(40, 30, 60)
            canvas.drawRoundRect(bannerRect, 16f, 16f, paint)

            paint.color = Color.rgb(255, 190, 80)
            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("CHAPTER $chNum", 90f, 920f, paint)

            paint.color = Color.WHITE
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(chTitle, 90f, 970f, paint)

        } else {
            // Standard Manga Panels Layout
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(60, 60, 80)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val panelFill = Paint().apply { color = Color.rgb(26, 26, 36) }

            // Panel 1 (Top)
            val p1 = RectF(40f, 50f, width - 40f, 380f)
            canvas.drawRect(p1, panelFill)
            canvas.drawRect(p1, borderPaint)

            // Skyline illustration
            paint.color = Color.rgb(45, 45, 65)
            for (bx in 60..700 step 70) {
                val bH = 150 + ((bx * 37) % 160)
                canvas.drawRect(bx.toFloat(), 380f - bH, bx.toFloat() + 55f, 380f, paint)
            }

            // Dialogue bubble 1
            drawSpeechBubble(canvas, 100f, 90f, 340f, 110f, "Neo Tokyo Sector 7...\nMidnight Protocol active.")

            // Panel 2 (Middle Left)
            val p2 = RectF(40f, 410f, 380f, 760f)
            canvas.drawRect(p2, panelFill)
            canvas.drawRect(p2, borderPaint)
            drawSoundEffect(canvas, 100f, 500f, "SHHH...")
            drawSpeechBubble(canvas, 60f, 560f, 300f, 100f, "Target detected at\ngrid 402.")

            // Panel 3 (Middle Right)
            val p3 = RectF(410f, 410f, width - 40f, 760f)
            canvas.drawRect(p3, panelFill)
            canvas.drawRect(p3, borderPaint)
            drawKatanaGlow(canvas, p3)
            drawSpeechBubble(canvas, 430f, 440f, 310f, 90f, "No turning back now.")

            // Panel 4 (Bottom Full Width)
            val p4 = RectF(40f, 790f, width - 40f, 1120f)
            canvas.drawRect(p4, panelFill)
            canvas.drawRect(p4, borderPaint)

            if (page == totalPages) {
                drawSpeechBubble(canvas, 80f, 840f, 400f, 110f, "Next sector ahead...\nThe hunt continues.")
                paint.color = Color.rgb(255, 75, 120)
                paint.textSize = 30f
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("TO BE CONTINUED...", width - 360f, 1060f, paint)
            } else {
                drawSpeedlinesAction(canvas, p4)
                drawSoundEffect(canvas, 280f, 960f, "ZAAAASH!!")
            }
        }

        // Page Number at bottom center
        paint.color = Color.GRAY
        paint.textSize = 18f
        paint.typeface = Typeface.MONOSPACE
        val footer = "- $page / $totalPages -"
        val textW = paint.measureText(footer)
        canvas.drawText(footer, (width - textW) / 2f, height - 16f, paint)

        return bitmap
    }

    private fun drawForestPage(chNum: Int, chTitle: String, page: Int, totalPages: Int): Bitmap {
        val width = 800
        val height = 1200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Manga natural soft ivory/charcoal paper
        canvas.drawColor(Color.rgb(248, 245, 238))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (page == 1) {
            // Soft atmospheric cover
            paint.color = Color.rgb(230, 240, 232)
            canvas.drawRect(30f, 30f, width - 30f, height - 30f, paint)

            paint.color = Color.rgb(46, 125, 50)
            paint.textSize = 28f
            paint.typeface = Typeface.SERIF
            canvas.drawText("WHISPERS OF THE", 60f, 120f, paint)

            paint.color = Color.rgb(27, 94, 32)
            paint.textSize = 58f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("FOREST SPIRIT", 60f, 190f, paint)

            // Torii Gate drawing
            val gatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(180, 50, 40)
                style = Paint.Style.STROKE
                strokeWidth = 14f
            }
            // Gate top bar
            canvas.drawLine(200f, 480f, 600f, 480f, gatePaint)
            canvas.drawLine(240f, 520f, 560f, 520f, gatePaint)
            // Gate pillars
            canvas.drawLine(280f, 480f, 280f, 760f, gatePaint)
            canvas.drawLine(520f, 480f, 520f, 760f, gatePaint)

            // Lantern glow
            paint.color = Color.rgb(255, 215, 64)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(400f, 620f, 36f, paint)

            val banner = RectF(60f, 860f, width - 60f, 1030f)
            paint.color = Color.rgb(215, 230, 218)
            canvas.drawRoundRect(banner, 16f, 16f, paint)

            paint.color = Color.rgb(46, 125, 50)
            paint.textSize = 34f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("CHAPTER $chNum", 90f, 930f, paint)

            paint.color = Color.rgb(40, 50, 40)
            paint.textSize = 24f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText(chTitle, 90f, 980f, paint)
        } else {
            // Panels in Japanese organic style
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(80, 90, 80)
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            val panelBg = Paint().apply { color = Color.WHITE }

            val p1 = RectF(40f, 50f, width - 40f, 450f)
            canvas.drawRect(p1, panelBg)
            canvas.drawRect(p1, borderPaint)
            drawSpeechBubble(canvas, 80f, 80f, 360f, 110f, "The ancient trees were\nwhispering again today...", isDark = false)

            val p2 = RectF(40f, 480f, width - 40f, 820f)
            canvas.drawRect(p2, panelBg)
            canvas.drawRect(p2, borderPaint)
            drawSpeechBubble(canvas, 100f, 520f, 360f, 100f, "Look! By the roots of the cedar!", isDark = false)

            val p3 = RectF(40f, 850f, width - 40f, 1120f)
            canvas.drawRect(p3, panelBg)
            canvas.drawRect(p3, borderPaint)
            if (page == totalPages) {
                drawSpeechBubble(canvas, 100f, 890f, 380f, 110f, "A warm glow lit the path home.\nTo be continued...", isDark = false)
            } else {
                drawSpeechBubble(canvas, 100f, 890f, 380f, 100f, "A gentle presence watched over us.", isDark = false)
            }
        }

        // Page number
        paint.color = Color.DKGRAY
        paint.textSize = 18f
        paint.typeface = Typeface.MONOSPACE
        val footer = "- $page / $totalPages -"
        val textW = paint.measureText(footer)
        canvas.drawText(footer, (width - textW) / 2f, height - 16f, paint)

        return bitmap
    }

    private fun drawSpeechBubble(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, text: String, isDark: Boolean = true) {
        val bubbleRect = RectF(x, y, x + w, y + h)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) Color.rgb(240, 240, 245) else Color.WHITE
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        canvas.drawRoundRect(bubbleRect, 20f, 20f, bgPaint)
        canvas.drawRoundRect(bubbleRect, 20f, 20f, strokePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
        }
        val lines = text.split("\n")
        var currentY = y + 36f
        for (line in lines) {
            canvas.drawText(line, x + 18f, currentY, textPaint)
            currentY += 28f
        }
    }

    private fun drawSoundEffect(canvas: Canvas, x: Float, y: Float, text: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(255, 215, 64)
            textSize = 42f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(text, x, y, paint)
    }

    private fun drawKatanaGlow(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 229, 255)
            strokeWidth = 5f
        }
        canvas.drawLine(rect.left + 40f, rect.bottom - 40f, rect.right - 40f, rect.top + 40f, paint)
    }

    private fun drawSpeedlinesAction(canvas: Canvas, rect: RectF) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 255, 255, 255)
            strokeWidth = 2f
        }
        val cx = rect.centerX()
        val cy = rect.centerY()
        for (i in 0 until 24) {
            val angle = i * 15.0
            val rad = Math.toRadians(angle)
            val sx = cx + (Math.cos(rad) * 40).toFloat()
            val sy = cy + (Math.sin(rad) * 40).toFloat()
            val ex = cx + (Math.cos(rad) * 260).toFloat()
            val ey = cy + (Math.sin(rad) * 260).toFloat()
            canvas.drawLine(sx, sy, ex, ey, paint)
        }
    }
}
