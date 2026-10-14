package com.example.data.tracking

import android.util.Log
import com.example.data.db.MangaTrackEntity
import com.example.data.db.TrackDao
import com.example.data.preferences.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * AniList tracking, modelled on Mihon: the user links a series by hand, reading pushes progress
 * (only ever upwards), and progress read offline stays pending until a later sync gets through.
 */
class TrackingRepository(
    private val dao: TrackDao,
    private val prefs: PreferencesManager
) {
    private val api = AniListApi { prefs.aniListToken }
    private val syncLock = Mutex()

    fun observe(mangaId: String): Flow<MangaTrackEntity?> = dao.observe(mangaId)

    /** Stores [token] from the login redirect after checking it with AniList; returns the user name. */
    suspend fun login(token: String): String = withContext(Dispatchers.IO) {
        val viewer = AniListApi { token }.viewer()
        prefs.setAniListLogin(token, viewer.id, viewer.name, viewer.scoreFormat.name)
        viewer.name
    }

    fun logout() = prefs.clearAniListLogin()

    suspend fun search(query: String): List<AniListMedia> = io { api.search(query) }

    /** Links [mangaId] to [media], adopting the user's existing list entry or adding one with what was read locally. */
    suspend fun link(mangaId: String, media: AniListMedia, readChapter: Int?, readVolume: Int?) = io {
        val entry = api.findEntry(prefs.aniListUserId, media.id)
        val base = MangaTrackEntity(
            mangaId = mangaId,
            remoteId = media.id,
            title = media.title,
            coverUrl = media.coverUrl,
            totalChapters = media.chapters,
            totalVolumes = media.volumes
        ).withEntry(entry)
        val track = base.withRead(readChapter, readVolume, today())
            .let { if (it.status == null) it.copy(status = TrackStatus.PLANNING) else it }
        val saved = if (entry == null || track != base) track.withEntry(api.save(track)) else track
        dao.upsert(saved)
    }

    /** Saves a manual edit from the tracking sheet. */
    suspend fun update(track: MangaTrackEntity) = io {
        dao.upsert(track.withEntry(api.save(track)).copy(pendingSync = false))
    }

    /** Re-reads the entry from AniList, picking up changes made on the website or another app. */
    suspend fun refresh(mangaId: String) = io {
        // The score format can be changed on the website at any time
        prefs.setAniListScoreFormat(api.viewer().scoreFormat.name)
        syncLock.withLock {
            val local = dao.get(mangaId) ?: return@withLock
            if (local.pendingSync) return@withLock // the pending sync merges remote changes itself
            dao.upsert(local.withEntry(api.findEntry(prefs.aniListUserId, local.remoteId)))
        }
    }

    suspend fun unlink(mangaId: String) = dao.delete(mangaId)

    /** Called when a chapter is finished; does nothing for series that aren't linked. */
    suspend fun onChapterRead(mangaId: String, chapter: Int?, volume: Int?) {
        if (prefs.aniListToken == null || !prefs.trackAutoUpdate.value) return
        val local = dao.get(mangaId) ?: return
        val updated = local.withRead(chapter, volume, today())
        if (updated == local) return
        dao.upsert(updated.copy(pendingSync = true))
        sync(mangaId)
    }

    /** Retries progress that couldn't be sent earlier (e.g. read offline). */
    suspend fun syncPending() {
        if (prefs.aniListToken == null) return
        dao.getPending().forEach { sync(it.mangaId) }
    }

    private suspend fun sync(mangaId: String) = withContext(Dispatchers.IO) {
        syncLock.withLock {
            val local = dao.get(mangaId)?.takeIf { it.pendingSync } ?: return@withLock
            try {
                // Start from AniList's current entry so edits made elsewhere aren't overwritten,
                // then apply the progress read here
                val entry = api.findEntry(prefs.aniListUserId, local.remoteId)
                val remote = local.withEntry(entry)
                val merged = remote.withRead(local.progress, local.progressVolumes, today())
                val saved = if (entry == null || merged != remote) merged.withEntry(api.save(merged)) else remote
                dao.upsert(saved.copy(pendingSync = false))
            } catch (e: AniListAuthException) {
                prefs.clearAniListLogin()
            } catch (e: Exception) {
                Log.w(TAG, "AniList sync failed, will retry: ${e.message}")
            }
        }
    }

    /** Runs [block] on IO; an expired login logs the user out before the error reaches the UI. */
    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: AniListAuthException) {
            prefs.clearAniListLogin()
            throw e
        }
    }

    private fun today() = LocalDate.now().toString()

    private companion object {
        const val TAG = "TrackingRepository"
    }
}
