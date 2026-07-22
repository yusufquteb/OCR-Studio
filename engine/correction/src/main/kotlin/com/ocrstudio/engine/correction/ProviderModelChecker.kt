package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.OnlineProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Backs the Settings screen's "Refresh" button: asks the provider which of our curated model
 * IDs are actually live right now, so the UI can show a real count instead of a static list that
 * might have drifted (providers deprecate/rename models over time). Best-effort -- any network
 * failure just means the refresh reports nothing new, it never blocks using a model that was
 * already working.
 */
object ProviderModelChecker {

    /** Returns the set of model IDs (provider's own identifiers) currently listed by [provider]. */
    suspend fun fetchAvailableModelIds(provider: OnlineProvider, apiKey: String): Result<Set<String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val listUrl = provider.modelsListUrl ?: return@runCatching emptySet()
                when (provider) {
                    OnlineProvider.GOOGLE_AI_STUDIO -> {
                        val json = getJson("$listUrl?key=$apiKey", headers = emptyMap())
                        val models = json.getJSONArray("models")
                        (0 until models.length()).map {
                            models.getJSONObject(it).getString("name").removePrefix("models/")
                        }.toSet()
                    }
                    OnlineProvider.OPENROUTER, OnlineProvider.NVIDIA_NIM -> {
                        val headers = if (apiKey.isNotBlank()) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()
                        val json = getJson(listUrl, headers)
                        val data = json.getJSONArray("data")
                        (0 until data.length()).map { data.getJSONObject(it).getString("id") }.toSet()
                    }
                    OnlineProvider.HUGGING_FACE -> emptySet()
                }
            }
        }

    /** Hugging Face has no per-key model list; check one specific model's existence instead. */
    suspend fun huggingFaceModelExists(modelId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL("https://huggingface.co/api/models/$modelId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
            }
            val code = connection.responseCode
            connection.disconnect()
            code in 200..299
        }.getOrDefault(false)
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
        connection.disconnect()
        check(responseCode in 200..299) { "HTTP $responseCode: $text" }
        return JSONObject(text)
    }
}
