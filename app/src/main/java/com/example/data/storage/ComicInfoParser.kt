package com.example.data.storage

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Metadata from a chapter archive's ComicInfo.xml (the ComicRack format, widely used by comic readers and servers).
 * Only fields present in the file are set.
 */
data class ComicInfo(
    val series: String? = null,
    val summary: String? = null,
    val genres: List<String> = emptyList(),
    val writer: String? = null,
    val artist: String? = null,
    val publisher: String? = null,
    val year: Int? = null
) {
    val isEmpty: Boolean
        get() = series == null && summary == null && genres.isEmpty() && writer == null &&
                artist == null && publisher == null && year == null
}

object ComicInfoParser {
    private val FIELDS = setOf("Series", "Summary", "Genre", "Tags", "Writer", "Penciller", "Inker", "Publisher", "Year")

    /** Parses ComicInfo.xml; returns null when it is malformed or has none of the fields we show. */
    fun parse(input: InputStream): ComicInfo? {
        return try {
            val values = HashMap<String, String>()
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                // Read only the simple text fields we use: others such as <Pages> contain child elements,
                // and nextText() throws on those
                if (event == XmlPullParser.START_TAG && parser.depth == 2 && parser.name in FIELDS) {
                    val name = parser.name
                    val text = parser.nextText().trim()
                    if (text.isNotEmpty()) values[name] = text
                }
                event = parser.next()
            }
            fun list(key: String) = values[key]?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

            ComicInfo(
                series = values["Series"],
                summary = values["Summary"],
                genres = (list("Genre") + list("Tags")).distinctBy { it.lowercase() },
                writer = values["Writer"],
                artist = values["Penciller"] ?: values["Inker"],
                publisher = values["Publisher"],
                year = values["Year"]?.toIntOrNull()?.takeIf { it > 0 }
            ).takeUnless { it.isEmpty }
        } catch (e: Exception) {
            null
        }
    }
}
