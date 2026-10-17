package com.example.data.tracking

import com.example.data.Chapter
import com.example.data.db.MangaTrackEntity

/** AniList's MediaListStatus; the names are sent to the API as-is. */
enum class TrackStatus(val displayName: String) {
    CURRENT("Reading"),
    PLANNING("Plan to read"),
    COMPLETED("Completed"),
    PAUSED("Paused"),
    DROPPED("Dropped"),
    REPEATING("Rereading")
}

/**
 * The user's AniList score format. Scores are stored as AniList's raw 0–100 value; a format maps it to
 * the [steps] + 1 positions the user picks from (step 0 = no score) and to the text they'd see on AniList.
 */
enum class ScoreFormat(val steps: Int) {
    POINT_100(100),
    POINT_10_DECIMAL(100),
    POINT_10(10),
    POINT_5(5),
    POINT_3(3);

    fun toRaw(step: Int): Int = when (this) {
        POINT_100, POINT_10_DECIMAL -> step
        POINT_10 -> step * 10
        POINT_5 -> step * 20
        POINT_3 -> listOf(0, 35, 60, 85)[step] // AniList's own smiley thresholds
    }

    fun toStep(raw: Int): Int = when (this) {
        POINT_100, POINT_10_DECIMAL -> raw
        POINT_10 -> (raw + 5) / 10
        POINT_5 -> (raw + 10) / 20
        POINT_3 -> when {
            raw == 0 -> 0
            raw <= 35 -> 1
            raw <= 60 -> 2
            else -> 3
        }
    }.coerceIn(0, steps)

    fun label(raw: Int): String {
        val step = toStep(raw)
        if (step == 0) return "Not scored"
        return when (this) {
            POINT_100 -> "$step / 100"
            POINT_10_DECIMAL -> "%.1f / 10".format(step / 10f)
            POINT_10 -> "$step / 10"
            POINT_5 -> "★".repeat(step) + "☆".repeat(5 - step)
            POINT_3 -> listOf("😞", "😐", "😊")[step - 1]
        }
    }

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: POINT_10
    }
}

/** A search result. */
data class AniListMedia(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val format: String?,
    val year: Int?,
    val chapters: Int,
    val volumes: Int,
    val siteUrl: String?
)

/** The user's list entry for a media, as AniList has it. */
data class ListEntry(
    val libraryId: Long,
    val status: TrackStatus?,
    val progress: Int,
    val progressVolumes: Int,
    val score: Int,
    val startedAt: String?,
    val completedAt: String?
)

private val VOLUME = Regex("""(?i)\b(?:v|vol\.?|volume)\s*(\d+)""")
private val CHAPTER = Regex("""(?i)\b(?:c|ch\.?|chapter)\s*(\d+)""")

/**
 * What finishing [chapter] means for AniList: (chapter, volume). A file named only as a volume
 * ("Horimiya - Vol 3.cbz") counts towards volumes, since its number isn't a chapter number.
 */
fun trackNumbers(chapter: Chapter): Pair<Int?, Int?> {
    // An explicit marker wins: the scanner's number reads "v01 c005" as 1
    CHAPTER.find(chapter.fileName)?.groupValues?.get(1)?.toIntOrNull()?.let { return it to null }
    val volume = VOLUME.find(chapter.fileName)?.groupValues?.get(1)?.toIntOrNull()
    if (volume != null) return null to volume
    return chapter.chapterNumber.toInt().takeIf { it > 0 } to null
}

/**
 * Applies a finished chapter/volume: progress only ever goes up (so rereads change
 * nothing), reading something starts the entry, and reaching the total completes it.
 */
fun MangaTrackEntity.withRead(chapter: Int?, volume: Int?, today: String, startedOn: String = today): MangaTrackEntity {
    val chapters = maxOf(progress, chapter ?: 0)
    val volumes = maxOf(progressVolumes, volume ?: 0)
    if (chapters == progress && volumes == progressVolumes) return this
    val finished = (totalChapters > 0 && chapters >= totalChapters) || (totalVolumes > 0 && volumes >= totalVolumes)
    val newStatus = when {
        finished -> TrackStatus.COMPLETED
        status == null || status == TrackStatus.PLANNING || status == TrackStatus.PAUSED -> TrackStatus.CURRENT
        else -> status
    }
    return copy(
        progress = chapters,
        progressVolumes = volumes,
        status = newStatus,
        startedAt = startedAt ?: startedOn,
        completedAt = if (newStatus == TrackStatus.COMPLETED) completedAt ?: today else completedAt
    )
}

/** This track with the remote list entry's values; a missing entry means it isn't on the user's list. */
fun MangaTrackEntity.withEntry(entry: ListEntry?): MangaTrackEntity = copy(
    libraryId = entry?.libraryId,
    status = entry?.status,
    progress = entry?.progress ?: 0,
    progressVolumes = entry?.progressVolumes ?: 0,
    score = entry?.score ?: 0,
    startedAt = entry?.startedAt,
    completedAt = entry?.completedAt
)
