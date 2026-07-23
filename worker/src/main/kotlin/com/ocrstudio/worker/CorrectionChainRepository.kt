package com.ocrstudio.worker

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.CorrectionChainEntry
import com.ocrstudio.core.common.CorrectorKind
import com.ocrstudio.core.common.MAX_CORRECTION_CHAIN_SIZE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.correctionChainDataStore by preferencesDataStore(name = "correction_chain")

/**
 * Persists the user's chosen correction fallback chain: up to [MAX_CORRECTION_CHAIN_SIZE]
 * models (offline LiteRT-LM and/or online provider models), tried in order per text chunk until
 * one produces an accepted result. Storage order is insertion order; [executionOrder] is what
 * actually runs -- every OFFLINE entry before every ONLINE entry, per the user's explicit
 * requirement that on-device models are always tried before anything sent over the network.
 */
@Singleton
class CorrectionChainRepository @Inject constructor(
    @AppContext private val context: Context
) {
    private object Keys {
        val ENTRIES = stringPreferencesKey("entries")
    }

    val chain: Flow<List<CorrectionChainEntry>> = context.correctionChainDataStore.data.map { prefs ->
        decode(prefs[Keys.ENTRIES])
    }

    suspend fun currentChain(): List<CorrectionChainEntry> = chain.first()

    /** Returns false (no-op) if the chain is already full or this entry is already present. */
    suspend fun addEntry(entry: CorrectionChainEntry): Boolean {
        var added = false
        context.correctionChainDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES])
            if (current.size < MAX_CORRECTION_CHAIN_SIZE && current.none { it == entry }) {
                prefs[Keys.ENTRIES] = encode(current + entry)
                added = true
            }
        }
        return added
    }

    suspend fun removeEntry(entry: CorrectionChainEntry) {
        context.correctionChainDataStore.edit { prefs ->
            prefs[Keys.ENTRIES] = encode(decode(prefs[Keys.ENTRIES]).filterNot { it == entry })
        }
    }

    /**
     * Drops OFFLINE entries that can never run in this build (LiteRT-LM not compiled in --
     * see [BuildConfigFlags.liteRtLmAvailable]), so a chain configured before that was known
     * doesn't keep silently promising a correction pass it will never perform. No-op once the
     * chain no longer has any such entries.
     */
    suspend fun pruneUnavailableOfflineEntries() {
        if (BuildConfigFlags.liteRtLmAvailable()) return
        context.correctionChainDataStore.edit { prefs ->
            val current = decode(prefs[Keys.ENTRIES])
            val pruned = current.filterNot { it.kind == CorrectorKind.OFFLINE }
            if (pruned.size != current.size) prefs[Keys.ENTRIES] = encode(pruned)
        }
    }

    /** OFFLINE entries first (in the order added), then ONLINE entries (in the order added). */
    fun executionOrder(entries: List<CorrectionChainEntry>): List<CorrectionChainEntry> =
        entries.filter { it.kind == CorrectorKind.OFFLINE } + entries.filter { it.kind == CorrectorKind.ONLINE }

    private fun encode(entries: List<CorrectionChainEntry>): String =
        entries.joinToString("|") { "${it.kind.name}:${it.modelId}" }

    private fun decode(raw: String?): List<CorrectionChainEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split("|").mapNotNull { part ->
            val separatorIndex = part.indexOf(':')
            if (separatorIndex <= 0) return@mapNotNull null
            val kind = runCatching { CorrectorKind.valueOf(part.substring(0, separatorIndex)) }.getOrNull()
                ?: return@mapNotNull null
            CorrectionChainEntry(kind, part.substring(separatorIndex + 1))
        }
    }
}
