package com.ocrstudio.engine.parser

import android.content.Context
import org.json.JSONObject

/** Loads the bundled `assets/parser/hadith_books_ar.json` abbreviation -> full name map. */
object HadithAbbreviations {
    private const val ASSET_PATH = "parser/hadith_books_ar.json"

    fun load(context: Context): Map<String, String> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val obj = JSONObject(json)
        val result = mutableMapOf<String, String>()
        obj.keys().forEach { key -> result[key] = obj.getString(key) }
        return result
    }
}
