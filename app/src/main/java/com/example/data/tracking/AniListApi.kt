package com.example.data.tracking

import com.example.data.db.MangaTrackEntity
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown when AniList rejects the token (expired or revoked): the user has to log in again. */
class AniListAuthException : IOException("AniList login expired, log in again")

/** Minimal AniList GraphQL client; blocking, call it off the main thread. */
class AniListApi(private val token: () -> String?) {

    /** The logged-in user's id, name and score format. */
    fun viewer(): Viewer {
        val v = post("query { Viewer { id name mediaListOptions { scoreFormat } } }").getJSONObject("Viewer")
        return Viewer(
            id = v.getLong("id"),
            name = v.getString("name"),
            scoreFormat = ScoreFormat.from(v.optJSONObject("mediaListOptions")?.optStringOrNull("scoreFormat"))
        )
    }

    data class Viewer(val id: Long, val name: String, val scoreFormat: ScoreFormat)

    fun search(query: String): List<AniListMedia> {
        val data = post(
            """
            query (${'$'}q: String) {
              Page(perPage: 30) {
                media(search: ${'$'}q, type: MANGA, sort: SEARCH_MATCH) {
                  id format chapters volumes siteUrl
                  title { userPreferred }
                  coverImage { large }
                  startDate { year }
                }
              }
            }
            """.trimIndent(),
            JSONObject().put("q", query)
        )
        val media = data.getJSONObject("Page").getJSONArray("media")
        return List(media.length()) { i ->
            val m = media.getJSONObject(i)
            AniListMedia(
                id = m.getLong("id"),
                title = m.getJSONObject("title").optString("userPreferred"),
                coverUrl = m.optJSONObject("coverImage")?.optStringOrNull("large"),
                format = m.optStringOrNull("format"),
                year = m.optJSONObject("startDate")?.optIntOrNull("year"),
                chapters = m.optInt("chapters", 0),
                volumes = m.optInt("volumes", 0),
                siteUrl = m.optStringOrNull("siteUrl")
            )
        }
    }

    /** The user's list entry for [mediaId], or null when it isn't on their list. */
    fun findEntry(userId: Long, mediaId: Long): ListEntry? {
        val data = post(
            """
            query (${'$'}userId: Int, ${'$'}mediaId: Int) {
              Page {
                mediaList(userId: ${'$'}userId, mediaId: ${'$'}mediaId, type: MANGA) { $ENTRY_FIELDS }
              }
            }
            """.trimIndent(),
            JSONObject().put("userId", userId).put("mediaId", mediaId)
        )
        val list = data.getJSONObject("Page").getJSONArray("mediaList")
        return if (list.length() == 0) null else parseEntry(list.getJSONObject(0))
    }

    /** Writes [track]'s status, progress, score and dates to AniList; returns the saved entry. */
    fun save(track: MangaTrackEntity): ListEntry {
        val vars = JSONObject()
            .put("mediaId", track.remoteId)
            .put("status", track.status?.name ?: TrackStatus.PLANNING.name)
            .put("progress", track.progress)
            .put("progressVolumes", track.progressVolumes)
            .put("scoreRaw", track.score)
            .put("startedAt", fuzzyDate(track.startedAt))
            .put("completedAt", fuzzyDate(track.completedAt))
        val data = post(
            """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}progressVolumes: Int,
                      ${'$'}scoreRaw: Int, ${'$'}startedAt: FuzzyDateInput, ${'$'}completedAt: FuzzyDateInput) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress,
                                 progressVolumes: ${'$'}progressVolumes, scoreRaw: ${'$'}scoreRaw,
                                 startedAt: ${'$'}startedAt, completedAt: ${'$'}completedAt) { $ENTRY_FIELDS }
            }
            """.trimIndent(),
            vars
        )
        return parseEntry(data.getJSONObject("SaveMediaListEntry"))
    }

    private fun post(query: String, variables: JSONObject = JSONObject()): JSONObject {
        val conn = URL(ENDPOINT).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            token()?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            conn.outputStream.use {
                it.write(JSONObject().put("query", query).put("variables", variables).toString().toByteArray())
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            // AniList answers a bad token with 401, or 400 "Invalid token"
            if (code == 401 || body.contains("Invalid token")) throw AniListAuthException()
            val json = runCatching { JSONObject(body) }.getOrNull()
            val error = json?.optJSONArray("errors")?.optJSONObject(0)?.optString("message")
            if (code !in 200..299 || json == null || error != null) {
                throw IOException("AniList: ${error ?: "HTTP $code"}")
            }
            return json.getJSONObject("data")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseEntry(e: JSONObject) = ListEntry(
        libraryId = e.getLong("id"),
        status = e.optStringOrNull("status")?.let { s -> TrackStatus.entries.firstOrNull { it.name == s } },
        progress = e.optInt("progress", 0),
        progressVolumes = e.optInt("progressVolumes", 0),
        score = e.optInt("scoreRaw", 0),
        startedAt = isoDate(e.optJSONObject("startedAt")),
        completedAt = isoDate(e.optJSONObject("completedAt"))
    )

    /** AniList dates may be partial (year only); missing parts become 1. */
    private fun isoDate(d: JSONObject?): String? {
        val year = d?.optIntOrNull("year") ?: return null
        return "%04d-%02d-%02d".format(year, d.optIntOrNull("month") ?: 1, d.optIntOrNull("day") ?: 1)
    }

    /** An all-null date clears the field on AniList. */
    private fun fuzzyDate(iso: String?): JSONObject {
        val parts = iso?.split('-')?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 3 }
        return JSONObject()
            .put("year", parts?.get(0) ?: JSONObject.NULL)
            .put("month", parts?.get(1) ?: JSONObject.NULL)
            .put("day", parts?.get(2) ?: JSONObject.NULL)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)

    companion object {
        const val CLIENT_ID = "10495"
        // Implicit grant: AniList redirects to mangareader://anilist-auth#access_token=... (a 1-year token)
        const val LOGIN_URL = "https://anilist.co/api/v2/oauth/authorize?client_id=$CLIENT_ID&response_type=token"
        private const val ENDPOINT = "https://graphql.anilist.co"
        private const val ENTRY_FIELDS =
            "id status progress progressVolumes scoreRaw: score(format: POINT_100) " +
                "startedAt { year month day } completedAt { year month day }"
    }
}
