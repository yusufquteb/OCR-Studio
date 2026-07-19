package com.ocrstudio.core.database.repository

import com.ocrstudio.core.database.AppDatabase
import com.ocrstudio.core.database.dao.PageSearchHit
import javax.inject.Inject

class SearchRepository @Inject constructor(
    private val db: AppDatabase
) {
    /**
     * Builds an FTS5 MATCH expression from free-form user input: strip characters that would
     * otherwise be interpreted as FTS query syntax, then quote each token so words like "AND"
     * or punctuation don't get misparsed as operators, and OR them together.
     */
    private fun buildMatchQuery(rawQuery: String): String {
        val tokens = rawQuery
            .trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.replace("\"", "") }
        if (tokens.isEmpty()) return "\"\""
        return tokens.joinToString(separator = " OR ") { "\"$it\"" }
    }

    suspend fun search(query: String, limit: Int = 200): List<PageSearchHit> =
        db.searchDao().searchPages(buildMatchQuery(query), limit)

    suspend fun searchInJob(query: String, jobId: String, limit: Int = 200): List<PageSearchHit> =
        db.searchDao().searchPagesInJob(buildMatchQuery(query), jobId, limit)
}
