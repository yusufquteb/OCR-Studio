package com.ocrstudio.engine.parser

import android.content.Context
import org.json.JSONArray

/** Loads the bundled `assets/parser/common_roots_ar.json` starter root list. */
object RootsDictionary {
    private const val ASSET_PATH = "parser/common_roots_ar.json"

    fun load(context: Context): Set<String> {
        val json = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val array = JSONArray(json)
        val result = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            result.add(array.getString(i))
        }
        return result
    }
}
