package com.example.engine

import java.text.Normalizer

/**
 * Normalises and compares page names.
 *
 * Two names match when their [comparisonKey]s are equal (not "contains"). The key ignores case, extra spaces,
 * curly vs straight quotes, and "fancy text": page names written with Unicode look-alike letters such as
 * 𝓛𝓮𝓸𝓷𝓸𝓻, 𝔄𝔫𝔧𝔢𝔩𝔦𝔫𝔞, 𝐁𝐨𝐥𝐝, ⓒⓘⓡⓒⓛⓔⓓ, ｗｉｄｅ or ꜱᴍᴀʟʟ ᴄᴀᴘꜱ match the plain name ("Leonor", "Anjelina", ...).
 * Emoji and decorations (👑, ✨, ꧁ ꧂, strike-through lines) are ignored as well.
 */
object NameMatcher {

    /** Line breaks and repeated spaces to one space, curly apostrophes to straight. Used for typing a name. */
    fun normalize(name: String): String {
        return name
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('ʼ', '\'')
            .replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Small capital and other look-alike letters that Unicode does not fold to plain letters by itself. */
    private val lookalikes: Map<Int, String> = mapOf(
        'ᴀ'.code to "a", 'ʙ'.code to "b", 'ᴄ'.code to "c", 'ᴅ'.code to "d", 'ᴇ'.code to "e", 'ꜰ'.code to "f",
        'ɢ'.code to "g", 'ʜ'.code to "h", 'ɪ'.code to "i", 'ᴊ'.code to "j", 'ᴋ'.code to "k", 'ʟ'.code to "l",
        'ᴍ'.code to "m", 'ɴ'.code to "n", 'ᴏ'.code to "o", 'ᴘ'.code to "p", 'ǫ'.code to "q", 'ʀ'.code to "r",
        'ꜱ'.code to "s", 'ᴛ'.code to "t", 'ᴜ'.code to "u", 'ᴠ'.code to "v", 'ᴡ'.code to "w", 'ʏ'.code to "y",
        'ᴢ'.code to "z", 'ı'.code to "i", 'ȷ'.code to "j", 'ø'.code to "o", 'Ø'.code to "o", 'ł'.code to "l",
        'Ł'.code to "l", 'đ'.code to "d", 'Đ'.code to "d", 'ß'.code to "ss", 'æ'.code to "ae", 'Æ'.code to "ae",
    )

    /**
     * The form of a name used for comparing: compatibility-folded (fancy letters to plain ones), accents and
     * combining marks removed, look-alike letters replaced, only letters and digits kept, single spaces, lower case.
     */
    fun comparisonKey(name: String): String {
        val folded = Normalizer.normalize(normalize(name), Normalizer.Form.NFKC)
        val replaced = StringBuilder()
        var i = 0
        while (i < folded.length) {
            val cp = folded.codePointAt(i)
            replaced.append(lookalikes[cp] ?: String(Character.toChars(cp)))
            i += Character.charCount(cp)
        }
        val decomposed = Normalizer.normalize(replaced, Normalizer.Form.NFKD)
        val key = StringBuilder()
        i = 0
        while (i < decomposed.length) {
            val cp = decomposed.codePointAt(i)
            when {
                Character.isLetterOrDigit(cp) -> key.appendCodePoint(Character.toLowerCase(cp))
                Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> key.append(' ')
                // Combining marks, emoji, symbols and punctuation are left out.
            }
            i += Character.charCount(cp)
        }
        return key.toString().replace(Regex(" +"), " ").trim()
    }

    fun matches(name1: String?, name2: String?): Boolean {
        if (name1 == null || name2 == null) return false
        val k1 = comparisonKey(name1)
        val k2 = comparisonKey(name2)
        if (k1.isEmpty() || k2.isEmpty()) {
            return normalize(name1).equals(normalize(name2), ignoreCase = true)
        }
        return k1 == k2
    }
}
