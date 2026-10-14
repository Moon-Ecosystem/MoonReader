package com.example.data.storage

/**
 * Turns scene-style folder/archive names into a tracker search query, e.g.
 * "Frankenstein - Junji Ito Story Collection (2018) (Digital) (1r0n).cbz" -> "Frankenstein - Junji Ito Story Collection".
 * Only used to pre-fill the search box; the user still confirms the match.
 */
object TitleCleaner {
    private val EXTENSION = Regex("""\.(cbz|zip|cbr|rar|7z)$""", RegexOption.IGNORE_CASE)
    // Brackets escaped everywhere: Android's ICU regex rejects a bare "]" or "}" that the JVM accepts
    private val BRACKETED = Regex("""\([^)]*\)|\[[^\]]*\]|\{[^}]*\}""")
    private val YEAR = Regex("""[(\[]\s*((?:19|20)\d{2})\s*[)\]]""")

    // A volume/chapter marker and everything after it: "v01", "Vol. 2", "Volume 3", "c097", "Ch. 12", "Chapter 5"
    private val NUMBERING = Regex("""\b(?:v|vol\.?|volume|c|ch\.?|chapter)\s*\d.*$""", RegexOption.IGNORE_CASE)

    fun clean(name: String): String {
        val base = name.trim().replace(EXTENSION, "")
        var title = base.replace('_', ' ')
        // "Some.Manga.v01" style names use dots as spaces
        if (' ' !in title) title = title.replace('.', ' ')
        title = title.replace(BRACKETED, " ")
            .replace(NUMBERING, "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('-', '–', ':', ',', ' ')
        return title.ifEmpty { base }
    }

    /** A release year written in brackets, e.g. "(2018)"; used to rank search results, never to filter them. */
    fun year(name: String): Int? = YEAR.find(name)?.groupValues?.get(1)?.toInt()
}
