package com.ocrstudio.core.common

/**
 * How a job handles Arabic diacritics (tashkeel/harakat):
 * - EXACT: never let the correction pipeline rewrite text, so whatever diacritics the OCR
 *   engine recognized (or didn't) pass through untouched. For Qur'an/hadith/heritage texts
 *   where preserving the source exactly matters more than fixing errors.
 * - NORMAL: today's default behavior -- rule-engine + configured AI correctors run as usual,
 *   with no special diacritics handling.
 * - SMART: same correctors as NORMAL, but if they end up adding diacritics the OCR text didn't
 *   have, the resulting page is flagged so Review can show "diacritics completed by AI".
 */
enum class TashkeelMode { EXACT, NORMAL, SMART }
