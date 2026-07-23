package com.ocrstudio.app.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ocrstudio.core.common.AppContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val defaultBatchSize: Int = 20,
    val defaultDpi: Int = 300,
    val keepImagesByDefault: Boolean = false,
    val batteryConstraintOnly: Boolean = false
)

class SettingsRepository @Inject constructor(
    @AppContext private val context: Context
) {
    private object Keys {
        val BATCH_SIZE = intPreferencesKey("default_batch_size")
        val DPI = intPreferencesKey("default_dpi")
        val KEEP_IMAGES = booleanPreferencesKey("keep_images_by_default")
        val BATTERY_CONSTRAINT = booleanPreferencesKey("battery_constraint_only")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETED] ?: false
    }

    suspend fun setOnboardingCompleted(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = value }
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            defaultBatchSize = prefs[Keys.BATCH_SIZE] ?: 20,
            defaultDpi = prefs[Keys.DPI] ?: 300,
            keepImagesByDefault = prefs[Keys.KEEP_IMAGES] ?: false,
            batteryConstraintOnly = prefs[Keys.BATTERY_CONSTRAINT] ?: false
        )
    }

    suspend fun setDefaultBatchSize(value: Int) {
        context.dataStore.edit { it[Keys.BATCH_SIZE] = value }
    }

    suspend fun setDefaultDpi(value: Int) {
        context.dataStore.edit { it[Keys.DPI] = value }
    }

    suspend fun setKeepImagesByDefault(value: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_IMAGES] = value }
    }

    suspend fun setBatteryConstraintOnly(value: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_CONSTRAINT] = value }
    }

    fun clearCache() {
        context.cacheDir.deleteRecursively()
    }
}
