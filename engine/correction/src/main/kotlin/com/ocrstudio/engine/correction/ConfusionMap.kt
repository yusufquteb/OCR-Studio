package com.ocrstudio.engine.correction

/** OCR letter-confusion groups used to generate correction candidates. */
object ConfusionMap {
    /** Confused specifically in word-initial position (similar dot patterns). */
    val INITIAL_POSITION_GROUP: List<Char> = listOf('ب', 'ت', 'ث', 'ن')

    /** Confused regardless of position within the word. */
    val ANY_POSITION_GROUPS: List<List<Char>> = listOf(
        listOf('ر', 'ز'),
        listOf('د', 'ذ'),
        listOf('ح', 'ج', 'خ'),
        listOf('ع', 'غ'),
        listOf('ص', 'ض'),
        listOf('ط', 'ظ'),
        listOf('س', 'ش')
    )

    /** Generates all single-substitution candidate words for [word] (excludes the original). */
    fun candidates(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        val results = mutableSetOf<String>()

        val firstChar = word[0]
        if (firstChar in INITIAL_POSITION_GROUP) {
            for (replacement in INITIAL_POSITION_GROUP) {
                if (replacement != firstChar) {
                    results.add(replacement + word.substring(1))
                }
            }
        }

        for (i in word.indices) {
            val c = word[i]
            val group = ANY_POSITION_GROUPS.firstOrNull { c in it } ?: continue
            for (replacement in group) {
                if (replacement != c) {
                    results.add(word.substring(0, i) + replacement + word.substring(i + 1))
                }
            }
        }

        results.remove(word)
        return results.toList()
    }
}
