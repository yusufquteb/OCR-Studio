package com.ocrstudio.app.ui.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ocrstudio.core.common.AppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.recentSearchesDataStore by preferencesDataStore(name = "recent_searches")
private const val MAX_RECENT_SEARCHES = 8
private const val SEPARATOR = ""

/** Persists the user's last few search queries so Search has something useful to show before
 *  they've typed anything, instead of a blank screen. */
@Singleton
class RecentSearchesRepository @Inject constructor(
    @AppContext private val context: Context
) {
    private object Keys {
        val QUERIES = stringPreferencesKey("queries")
    }

    val recentSearches: Flow<List<String>> = context.recentSearchesDataStore.data.map { prefs ->
        decode(prefs[Keys.QUERIES])
    }

    suspend fun record(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        context.recentSearchesDataStore.edit { prefs ->
            val current = decode(prefs[Keys.QUERIES]).filterNot { it.equals(trimmed, ignoreCase = true) }
            prefs[Keys.QUERIES] = encode((listOf(trimmed) + current).take(MAX_RECENT_SEARCHES))
        }
    }

    private fun encode(queries: List<String>): String = queries.joinToString(SEPARATOR)

    private fun decode(raw: String?): List<String> =
        raw?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
}
