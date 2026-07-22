package com.ocrstudio.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.OnlineCorrectionConfig
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.core.common.OnlineProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onlineCorrectionDataStore by preferencesDataStore(name = "online_correction")

/**
 * Persists the optional online-correction provider/model choice (non-sensitive, plain DataStore)
 * and each provider's own API key (encrypted at rest via EncryptedSharedPreferences, since these
 * are real credentials unlike anything else this app stores). A user can save a key for more than
 * one provider, but only one provider/model pair is ever "active" (used for the next job's
 * correction) at a time -- enabling one implicitly is the only selection, not a fallback chain.
 */
@Singleton
class OnlineCorrectionRepository @Inject constructor(
    @AppContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("enabled")
        val MODEL_ID = stringPreferencesKey("model_id")
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "online_correction_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    val config: Flow<OnlineCorrectionConfig> = context.onlineCorrectionDataStore.data.map { prefs ->
        val modelId = prefs[Keys.MODEL_ID]
        val provider = modelId?.let { OnlineModelCatalog.byId(it)?.provider }
        OnlineCorrectionConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            modelId = modelId,
            apiKey = provider?.let { apiKeyFor(it) } ?: ""
        )
    }

    /** One-shot read for use inside a single page's processing, outside of Compose collection. */
    suspend fun currentConfig(): OnlineCorrectionConfig = config.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.onlineCorrectionDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    suspend fun setModelId(modelId: String?) {
        context.onlineCorrectionDataStore.edit { prefs ->
            if (modelId == null) prefs.remove(Keys.MODEL_ID) else prefs[Keys.MODEL_ID] = modelId
        }
    }

    fun apiKeyFor(provider: OnlineProvider): String = encryptedPrefs.getString(keyPrefFor(provider), "") ?: ""

    fun setApiKeyFor(provider: OnlineProvider, apiKey: String) {
        encryptedPrefs.edit().putString(keyPrefFor(provider), apiKey).apply()
    }

    private fun keyPrefFor(provider: OnlineProvider) = "api_key_${provider.name}"
}
