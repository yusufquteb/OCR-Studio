package com.ocrstudio.engine.correction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleEngineTest {

    private val dictionary = Dictionary.fromWords(
        listOf("كتاب", "كتب", "بيت", "رجل", "حديث", "سمع", "قال", "علم")
    )
    private val ruleEngine = RuleEngine(dictionary)

    @Test
    fun `word already in dictionary is left unchanged`() {
        val (word, found) = ruleEngine.correctWord("كتاب")
        assertEquals("كتاب", word)
        assertTrue(found)
    }

    @Test
    fun `stray tatweel is stripped before lookup`() {
        val (word, found) = ruleEngine.correctWord("كـتاب")
        assertEquals("كتاب", word)
        assertTrue(found)
    }

    @Test
    fun `alif maqsura vs ya variant is corrected when only one form is in the dictionary`() {
        // "بيت" is in the dictionary; feed a ya/alif-maqsura swapped variant of a word ending
        // differently to exercise the candidate-variant path without accidentally matching itself.
        val dict = Dictionary.fromWords(listOf("سمى"))
        val engine = RuleEngine(dict)
        val (word, found) = engine.correctWord("سمي")
        assertEquals("سمى", word)
        assertTrue(found)
    }

    @Test
    fun `ocr confusion candidate is accepted only when original is absent and candidate present`() {
        // "علم" is in the dictionary; "علن" (م/ن not a confusion pair here) should NOT match --
        // instead test with a real confusion pair: ر/ز. Dictionary has "رجل"; feed "زجل".
        val (word, found) = ruleEngine.correctWord("زجل")
        assertEquals("رجل", word)
        assertTrue(found)
    }

    @Test
    fun `word not in dictionary and no valid candidate is left unchanged and marked not found`() {
        val (word, found) = ruleEngine.correctWord("قصقص")
        assertEquals("قصقص", word)
        assertFalse(found)
    }

    @Test
    fun `existing dictionary word is never overridden by a confusion candidate`() {
        // "رجل" itself is in the dictionary, so it must be returned as-is even though a
        // confusion candidate ("زجل") could theoretically exist for it in another dictionary.
        val (word, found) = ruleEngine.correctWord("رجل")
        assertEquals("رجل", word)
        assertTrue(found)
    }

    @Test
    fun `correctText preserves layout and only substitutes recognized arabic tokens`() {
        val result = ruleEngine.correctText("زجل قال حديث 123")
        assertTrue(result.correctedText.contains("رجل"))
        assertTrue(result.correctedText.contains("123"))
    }

    @Test
    fun `dictionary hit rate reflects fraction of tokens resolved`() {
        val result = ruleEngine.correctText("كتاب قصقص")
        assertEquals(0.5f, result.dictionaryHitRate, 0.001f)
    }

    @Test
    fun `empty text yields perfect hit rate and empty output`() {
        val result = ruleEngine.correctText("")
        assertEquals(1.0f, result.dictionaryHitRate, 0.001f)
        assertEquals("", result.correctedText)
    }
}
