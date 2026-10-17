package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.db.MangaEntity
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Home screen row of the series being read; tapping one resumes it at its saved page. Only refreshed when the
 * app goes to the background (the only time progress changes), so it does no background work of its own.
 */
class ContinueReadingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = render(context, ids)

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        render(context, intArrayOf(id))

    private fun render(context: Context, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                update(context, ids)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RESUME = "com.example.action.RESUME_MANGA"
        const val EXTRA_MANGA_ID = "manga_id"

        private class Slot(val root: Int, val cover: Int, val title: Int, val chapter: Int)

        private val SLOTS = listOf(
            Slot(R.id.slot_0, R.id.cover_0, R.id.title_0, R.id.chapter_0),
            Slot(R.id.slot_1, R.id.cover_1, R.id.title_1, R.id.chapter_1),
            Slot(R.id.slot_2, R.id.cover_2, R.id.title_2, R.id.chapter_2),
            Slot(R.id.slot_3, R.id.cover_3, R.id.title_3, R.id.chapter_3)
        )

        /**
         * How many covers fit side by side. Each slot is as wide as a cover of the widget's height (covers are about
         * 0.7 as wide as tall, below them sit two text lines and around them 8 dp padding), but at least 80 dp, so a
         * tall widget shows fewer, bigger covers instead of thin strips, and a small one only the latest series.
         */
        internal fun slotCount(widthDp: Int, heightDp: Int): Int {
            val coverHeight = (heightDp - 16 - 34).coerceAtLeast(0)
            val slotWidth = maxOf(80f, coverHeight * 0.7f + 6f)
            return (widthDp / slotWidth).toInt().coerceIn(1, SLOTS.size)
        }

        /** Redraws every placed widget; called when the app goes to the background. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, ContinueReadingWidget::class.java))
            if (ids.isEmpty()) return
            CoroutineScope(Dispatchers.IO).launch { update(context, ids) }
        }

        private suspend fun update(context: Context, ids: IntArray) {
            val manager = AppWidgetManager.getInstance(context)
            // Covers on the home screen would get around the PIN, so show only a placeholder while locked
            val locked = PreferencesManager(context).isAppLockEnabled.value
            val reading = if (locked) emptyList()
            else AppDatabase.getDatabase(context).mangaDao().getInProgressManga(SLOTS.size)
            val covers = reading.associate { it.id to it.coverPath?.let(::decodeCover) }
            for (id in ids) {
                // Portrait size: launchers report it as the minimum width and the maximum height
                val options = manager.getAppWidgetOptions(id)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
                manager.updateAppWidget(id, views(context, reading.take(slotCount(width, height)), covers, locked))
            }
        }

        private fun views(
            context: Context,
            reading: List<MangaEntity>,
            covers: Map<String, Bitmap?>,
            locked: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
            val openApp = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (reading.isEmpty()) {
                views.setViewVisibility(R.id.slots, View.GONE)
                views.setViewVisibility(R.id.placeholder, View.VISIBLE)
                views.setTextViewText(
                    R.id.placeholder_text,
                    context.getString(if (locked) R.string.widget_locked else R.string.widget_empty)
                )
                views.setOnClickPendingIntent(R.id.placeholder, openApp)
                return views
            }
            SLOTS.forEachIndexed { i, slot ->
                val manga = reading.getOrNull(i)
                // Missing series keep their space so the covers don't stretch to fill the row
                views.setViewVisibility(slot.root, if (manga == null) View.INVISIBLE else View.VISIBLE)
                if (manga == null) return@forEachIndexed
                val cover = covers[manga.id]
                if (cover != null) views.setImageViewBitmap(slot.cover, cover)
                else views.setImageViewResource(slot.cover, R.drawable.ic_launcher_art)
                views.setTextViewText(slot.title, manga.title)
                views.setTextViewText(slot.chapter, manga.lastReadChapterTitle.orEmpty())
                val resume = Intent(context, MainActivity::class.java)
                    .setAction(ACTION_RESUME)
                    .putExtra(EXTRA_MANGA_ID, manga.id)
                views.setOnClickPendingIntent(
                    slot.root,
                    PendingIntent.getActivity(
                        context, i + 1, resume,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            return views
        }

        // Widget bitmaps are sent to the launcher and are size-limited, so decode covers small
        private fun decodeCover(path: String): Bitmap? = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outHeight / (sample * 2) >= 320) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
