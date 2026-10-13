package com.example.data.storage

import java.util.Comparator

/**
 * Natural order comparator that sorts filenames and chapter titles naturally:
 * e.g., "Chapter 2" comes before "Chapter 10".
 */
object NaturalOrderComparator : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        var ia = 0
        var ib = 0
        val nza = a.length
        val nzb = b.length

        while (ia < nza && ib < nzb) {
            val ca = a[ia]
            val cb = b[ib]

            if (ca.isDigit() && cb.isDigit()) {
                var startA = ia
                while (startA < nza && a[startA] == '0') startA++
                var endA = startA
                while (endA < nza && a[endA].isDigit()) endA++

                var startB = ib
                while (startB < nzb && b[startB] == '0') startB++
                var endB = startB
                while (endB < nzb && b[endB].isDigit()) endB++

                val lenA = endA - startA
                val lenB = endB - startB

                if (lenA != lenB) {
                    return lenA - lenB
                }

                for (k in 0 until lenA) {
                    val diff = a[startA + k] - b[startB + k]
                    if (diff != 0) return diff
                }

                ia = endA
                ib = endB
            } else {
                val diff = ca.lowercaseChar().compareTo(cb.lowercaseChar())
                if (diff != 0) return diff
                ia++
                ib++
            }
        }
        return nza - nzb
    }

    /**
     * Extracts an approximate numeric chapter number from a title or filename.
     * E.g. "Ch. 12 - Title" -> 12f, "Vol. 2 Ch. 4.5" -> 4.5f.
     */
    fun extractChapterNumber(name: String): Float {
        val regex = Regex("""(?i)(?:ch(?:apter)?\.?|c\b|#)\s*([0-9]+(?:\.[0-9]+)?)""")
        val match = regex.find(name)
        if (match != null) {
            match.groupValues[1].toFloatOrNull()?.let { return it }
        }
        // Fallback: look for any floating point / integer number
        val anyNumber = Regex("""([0-9]+(?:\.[0-9]+)?)""").find(name)
        return anyNumber?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }
}
