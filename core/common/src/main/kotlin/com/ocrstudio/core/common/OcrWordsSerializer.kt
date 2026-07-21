package com.ocrstudio.core.common

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes the raw (pre-correction) OcrWord list -- text + pixel-space bounding box -- so it
 * can be persisted as a PageRecord column and later reused to build a searchable PDF's invisible
 * text layer. Correction can rewrite/merge/split words, which would break any index-based
 * mapping back to boxes, so this intentionally captures the OCR engine's own words+boxes rather
 * than the corrected text.
 */
object OcrWordsSerializer {
    fun encode(words: List<OcrWord>): String {
        val array = JSONArray()
        words.forEach { word ->
            array.put(
                JSONObject().apply {
                    put("text", word.text)
                    put("confidence", word.confidence.toDouble())
                    put("left", word.left)
                    put("top", word.top)
                    put("right", word.right)
                    put("bottom", word.bottom)
                }
            )
        }
        return array.toString()
    }

    fun decode(json: String?): List<OcrWord> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).map { index ->
                val obj = array.getJSONObject(index)
                OcrWord(
                    text = obj.optString("text"),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    left = obj.optInt("left"),
                    top = obj.optInt("top"),
                    right = obj.optInt("right"),
                    bottom = obj.optInt("bottom")
                )
            }
        }.getOrDefault(emptyList())
    }
}
