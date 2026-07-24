package com.ocrstudio.core.common

import org.json.JSONObject

data class CorrectionScope(
    val dontChangeAnyWord: Boolean = false,
    val keepDiacriticsAsScanned: Boolean = false,
    val fixDiacriticsOnly: Boolean = false,
    val fixSpellingOnly: Boolean = false,
    val fixPunctuation: Boolean = true,
    val reformatParagraphs: Boolean = false
) {
    fun systemPromptSuffix(): String = buildString {
        when {
            dontChangeAnyWord -> append("IMPORTANT: Do not change any word. Return the text exactly as given.")
            fixDiacriticsOnly -> append("Fix diacritics (tashkeel/harakat) only. Do not change any words.")
            else -> {
                if (!fixSpellingOnly) append("Fix OCR errors and spelling. ")
                if (!fixPunctuation) append("Do not add or change punctuation. ")
                if (reformatParagraphs) append("Reformat paragraphs for readability.")
            }
        }
    }.trim()
}

object CorrectionScopeSerializer {
    fun encode(scope: CorrectionScope): String = JSONObject().apply {
        put("dontChangeAnyWord", scope.dontChangeAnyWord)
        put("keepDiacriticsAsScanned", scope.keepDiacriticsAsScanned)
        put("fixDiacriticsOnly", scope.fixDiacriticsOnly)
        put("fixSpellingOnly", scope.fixSpellingOnly)
        put("fixPunctuation", scope.fixPunctuation)
        put("reformatParagraphs", scope.reformatParagraphs)
    }.toString()

    fun decode(json: String): CorrectionScope = runCatching {
        val obj = JSONObject(json)
        CorrectionScope(
            dontChangeAnyWord = obj.optBoolean("dontChangeAnyWord", false),
            keepDiacriticsAsScanned = obj.optBoolean("keepDiacriticsAsScanned", false),
            fixDiacriticsOnly = obj.optBoolean("fixDiacriticsOnly", false),
            fixSpellingOnly = obj.optBoolean("fixSpellingOnly", false),
            fixPunctuation = obj.optBoolean("fixPunctuation", true),
            reformatParagraphs = obj.optBoolean("reformatParagraphs", false)
        )
    }.getOrDefault(CorrectionScope())
}
