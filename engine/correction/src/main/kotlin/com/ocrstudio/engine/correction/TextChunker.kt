package com.ocrstudio.engine.correction

/** Splits text into ~[targetSize]-character chunks, breaking at paragraph boundaries where possible. */
object TextChunker {
    fun chunk(text: String, targetSize: Int = 1500): List<String> {
        if (text.length <= targetSize) return if (text.isEmpty()) emptyList() else listOf(text)

        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<String>()
        val current = StringBuilder()

        for (paragraph in paragraphs) {
            if (current.isNotEmpty() && current.length + paragraph.length + 2 > targetSize) {
                chunks.add(current.toString())
                current.clear()
            }
            if (paragraph.length > targetSize) {
                // A single paragraph exceeds the target: flush what we have, then hard-split it.
                if (current.isNotEmpty()) {
                    chunks.add(current.toString())
                    current.clear()
                }
                var start = 0
                while (start < paragraph.length) {
                    val end = (start + targetSize).coerceAtMost(paragraph.length)
                    chunks.add(paragraph.substring(start, end))
                    start = end
                }
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }
        if (current.isNotEmpty()) chunks.add(current.toString())
        return chunks
    }
}
