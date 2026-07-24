package com.ocrstudio.core.common

enum class ReviewType {
    OCR_ERRORS,
    GRAMMAR,
    SPELLING,
    PUNCTUATION,
    FORMATTING,
    ACADEMIC,
    TRANSLATION;

    fun systemPromptSuffix(): String = when (this) {
        OCR_ERRORS -> ""
        GRAMMAR -> "Focus on grammatical correctness in classical Arabic. Fix grammar errors without changing wording."
        SPELLING -> "Fix spelling errors only. Do not change grammar, punctuation, or style."
        PUNCTUATION -> "Fix punctuation and harakat only. Do not change any words."
        FORMATTING -> "Fix paragraph formatting and spacing only. Do not change word content."
        ACADEMIC -> "Apply academic Arabic style. Fix OCR errors and improve formal register."
        TRANSLATION -> "Translate the Arabic text to English. Provide a natural, accurate English translation."
    }
}
