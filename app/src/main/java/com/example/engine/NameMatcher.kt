package com.example.engine

/**
 * Normalises and compares page names.
 * Replaces curly quotes (’ ‘ ʼ) with straight apostrophe ('),
 * collapses multiple spaces into one, trims whitespace, and ignores case.
 * Two names match ONLY when the normalised strings are strictly equal (not "contains").
 */
object NameMatcher {

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

    fun matches(name1: String?, name2: String?): Boolean {
        if (name1 == null || name2 == null) return false
        val n1 = normalize(name1)
        val n2 = normalize(name2)
        return n1.equals(n2, ignoreCase = true)
    }
}
