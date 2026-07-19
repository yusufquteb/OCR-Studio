package com.ocrstudio.engine.correction

import android.content.Context

/** Loads `assets/dictionary_ar.txt` (one word per line). Streams the file so size is unbounded. */
class Dictionary private constructor(private val words: Set<String>) {

    fun contains(word: String): Boolean = word in words

    val size: Int get() = words.size

    companion object {
        private const val ASSET_PATH = "dictionary_ar.txt"

        fun load(context: Context): Dictionary {
            val words = mutableSetOf<String>()
            context.assets.open(ASSET_PATH).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) words.add(trimmed)
                }
            }
            return Dictionary(words)
        }

        fun fromWords(words: Collection<String>): Dictionary = Dictionary(words.toSet())
    }
}
