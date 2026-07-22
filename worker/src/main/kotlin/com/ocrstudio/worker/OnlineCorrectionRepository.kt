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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onlineCorrectionDataStore by preferencesDataStore(name = "online_correction")
private const val API_KEY_PREF = "api_key"

/**
 * Persists the optional online-correction provider/model choice (non-sensitive, plain DataStore)
 * and the user's own API key (encrypted at rest via EncryptedSharedPreferences, since it's a
 * real credential unlike anything else this app stores).
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
        OnlineCorrectionConfig(
            enabled = prefs[Keys.ENABLED] ?: false,
            modelId = prefs[Keys.MODEL_ID],
            apiKey = encryptedPrefs.getString(API_KEY_PREF, "") ?: ""
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

    fun setApiKey(apiKey: String) {
        encryptedPrefs.edit().putString(API_KEY_PREF, apiKey).apply()
    }
}
