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
        ReadingSessionEntity::class,
        MangaTrackEntity::class,
        CollectionEntity::class,
        CollectionMangaEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mangaDao(): MangaDao
    abstract fun statisticsDao(): StatisticsDao
    abstract fun trackDao(): TrackDao
    abstract fun collectionDao(): CollectionDao
    abstract fun backupDao(): BackupDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `manga_tracks` (
                        `mangaId` TEXT NOT NULL,
                        `remoteId` INTEGER NOT NULL,
                        `libraryId` INTEGER,
                        `title` TEXT NOT NULL,
                        `coverUrl` TEXT,
                        `status` TEXT,
                        `progress` INTEGER NOT NULL,
                        `progressVolumes` INTEGER NOT NULL,
                        `totalChapters` INTEGER NOT NULL,
                        `totalVolumes` INTEGER NOT NULL,
                        `score` INTEGER NOT NULL,
                        `startedAt` TEXT,
                        `completedAt` TEXT,
                        `pendingSync` INTEGER NOT NULL,
                        PRIMARY KEY(`mangaId`)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `position` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `collection_manga` (`collectionId` INTEGER NOT NULL, " +
                        "`mangaId` TEXT NOT NULL, PRIMARY KEY(`collectionId`, `mangaId`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_collection_manga_mangaId` ON `collection_manga` (`mangaId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manga_reader_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
