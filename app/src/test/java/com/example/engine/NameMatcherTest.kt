package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Page names written in "fancy text" must match the plain names in the portal. */
class NameMatcherTest {

    @Test
    fun fancyLetterStylesMatchThePlainName() {
        val styles = listOf(
            "𝔄𝔫𝔧𝔢𝔩𝔦𝔫𝔞 𝔍𝔲𝔩𝔦𝔢 FC",          // gothic (fraktur)
            "𝕬𝖓𝖏𝖊𝖑𝖎𝖓𝖆 𝕵𝖚𝖑𝖎𝖊 𝕱𝕮",          // bold gothic
            "𝒜𝓃𝒿𝑒𝓁𝒾𝓃𝒶 𝒥𝓊𝓁𝒾𝑒 ℱ𝒞",          // script
            "𝓐𝓷𝓳𝓮𝓵𝓲𝓷𝓪 𝓙𝓾𝓵𝓲𝓮 𝓕𝓒",          // bold script
            "𝔸𝕟𝕛𝕖𝕝𝕚𝕟𝕒 𝕁𝕦𝕝𝕚𝕖 𝔽ℂ",          // outlined (double struck)
            "𝐀𝐧𝐣𝐞𝐥𝐢𝐧𝐚 𝐉𝐮𝐥𝐢𝐞 𝐅𝐂",          // bold
            "𝘈𝘯𝘫𝘦𝘭𝘪𝘯𝘢 𝘑𝘶𝘭𝘪𝘦 𝘍𝘊",          // italic
            "𝙰𝚗𝚓𝚎𝚕𝚒𝚗𝚊 𝙹𝚞𝚕𝚒𝚎 𝙵𝙲",          // typewriter
            "Ⓐⓝⓙⓔⓛⓘⓝⓐ Ⓙⓤⓛⓘⓔ ⒻⒸ",          // circled
            "Ａｎｊｅｌｉｎａ Ｊｕｌｉｅ ＦＣ",   // wide
            "ᴀɴᴊᴇʟɪɴᴀ ᴊᴜʟɪᴇ ꜰᴄ",              // small capitals
            "👑 Anjelina Julie FC ✨",         // emoji around the name
            "꧁Anjelina  Julie FC꧂",          // decorations and a double space
            "A̶n̶j̶e̶l̶i̶n̶a̶ J̶u̶l̶i̶e̶ F̶C̶",      // strike-through
            "anjelina julie fc",              // lower case
        )
        for (style in styles) {
            assertTrue("'$style' should match", NameMatcher.matches(style, "Anjelina Julie FC"))
        }
    }

    @Test
    fun namesFromThePhoneMatchInBothDirections() {
        assertTrue(NameMatcher.matches("𝓛𝓮𝓸𝓷𝓸𝓻 𝓡𝓸𝔂𝓪𝓵𝓽𝔂 𝓛𝓮𝓰𝓪𝓬𝔂", "Leonor Royalty Legacy"))
        assertTrue(NameMatcher.matches("Leonor Fc", "𝕷𝖊𝖔𝖓𝖔𝖗 𝕱𝖈"))
        assertTrue(NameMatcher.matches("Sofía Queen", "Sofia Queen"))
        assertTrue(NameMatcher.matches("Leonor’s Page", "Leonor's Page"))
    }

    @Test
    fun differentNamesDoNotMatch() {
        assertFalse(NameMatcher.matches("𝓛𝓮𝓸𝓷𝓸𝓻 Fc", "Leonor Royalty Legacy"))
        assertFalse(NameMatcher.matches("Sofia Queen", "Sofia Queen 2"))
        assertFalse(NameMatcher.matches("Anjelina Julie", "Anjelina Julie FC"))
        assertFalse(NameMatcher.matches("Leonor", null))
    }

    @Test
    fun theKeyIsPlainLowerCaseLetters() {
        assertEquals("leonor royalty legacy", NameMatcher.comparisonKey("𝓛𝓮𝓸𝓷𝓸𝓻 𝓡𝓸𝔂𝓪𝓵𝓽𝔂 𝓛𝓮𝓰𝓪𝓬𝔂"))
        assertEquals("anjelina julie fc", NameMatcher.comparisonKey("𝔄𝔫𝔧𝔢𝔩𝔦𝔫𝔞 𝔍𝔲𝔩𝔦𝔢 FC"))
    }
}
