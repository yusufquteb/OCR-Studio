package com.ocrstudio.engine.correction

/** Pure, dictionary-independent Arabic text normalization rules. */
object Normalization {
    private const val TATWEEL = 'ـ'

    private val ARABIC_TO_WESTERN_DIGITS = mapOf(
        '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
        '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
    )
    private val WESTERN_TO_ARABIC_DIGITS = ARABIC_TO_WESTERN_DIGITS.entries.associate { (k, v) -> v to k }

    /** Common presentation-form ligatures (U+FE70..FEFF range) collapsed to base letter sequences. */
    private val LIGATURE_MAP = mapOf(
        'ﻻ' to "لا", 'ﻼ' to "لا", // lam-alef isolated/final
        'ﻷ' to "لأ", 'ﻸ' to "لأ",
        'ﻹ' to "لإ", 'ﻺ' to "لإ",
        'ﻵ' to "لآ", 'ﻶ' to "لآ"
    )

    /** Arabic harakat/tashkeel: fathatan..sukun plus the extra Quranic marks up to 065F. */
    private val DIACRITIC_RANGE = 'ً'..'ٟ'

    fun hasDiacritics(word: String): Boolean = word.any { it in DIACRITIC_RANGE }

    fun stripDiacritics(word: String): String = word.filter { it !in DIACRITIC_RANGE }

    fun removeTatweel(word: String): String = word.filter { it != TATWEEL }

    fun collapseLigatures(word: String): String {
        val sb = StringBuilder()
        for (c in word) {
            sb.append(LIGATURE_MAP[c] ?: c)
        }
        return sb.toString()
    }

    fun arabicDigitsToWestern(text: String): String =
        text.map { ARABIC_TO_WESTERN_DIGITS[it] ?: it }.joinToString("")

    fun westernDigitsToArabic(text: String): String =
        text.map { WESTERN_TO_ARABIC_DIGITS[it] ?: it }.joinToString("")

    /** Baseline cleanup applied before any dictionary-based correction. */
    fun baseline(word: String): String = collapseLigatures(removeTatweel(word))

    /** Alternate spellings to try for final-ya/alif-maqsura and ta-marbuta/ha confusion, plus hamza forms. */
    fun candidateVariants(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val variants = mutableSetOf<String>()

        val last = word.last()
        when (last) {
            'ى' -> variants.add(word.dropLast(1) + 'ي')
            'ي' -> variants.add(word.dropLast(1) + 'ى')
            'ة' -> variants.add(word.dropLast(1) + 'ه')
            'ه' -> variants.add(word.dropLast(1) + 'ة')
        }

        val first = word.first()
        val hamzaForms = listOf('أ', 'إ', 'آ', 'ا')
        if (first in hamzaForms) {
            for (form in hamzaForms) {
                if (form != first) variants.add(form + word.substring(1))
            }
        }

        variants.remove(word)
        return variants.toList()
    }
}
