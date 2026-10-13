package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MangaEntity::class,
        ChapterProgressEntity::class,
        ReadingSessionEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun statisticsDao(): StatisticsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reading_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `mangaId` TEXT NOT NULL,
                        `chapterId` TEXT NOT NULL,
                        `chapterTitle` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER NOT NULL,
                        `durationMillis` INTEGER NOT NULL,
                        `startPage` INTEGER NOT NULL,
                        `endPage` INTEGER NOT NULL,
                        `pagesProgressed` INTEGER NOT NULL,
                        `completedChapter` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_mangaId` ON `reading_sessions` (`mangaId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_chapterId` ON `reading_sessions` (`chapterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_sessions_startedAt` ON `reading_sessions` (`startedAt`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manga_reader_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
