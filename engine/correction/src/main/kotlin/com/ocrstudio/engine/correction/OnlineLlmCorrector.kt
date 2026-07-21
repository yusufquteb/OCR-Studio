package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.LlmResult
import com.ocrstudio.core.common.OnlineProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SYSTEM_PROMPT =
    "You are an Arabic OCR proofreader. Fix OCR errors only. Do not add, remove, or rephrase " +
        "anything. Output the corrected text only, with no explanation or markdown formatting."

/**
 * Calls a user-configured online LLM provider (Google AI Studio, OpenRouter, NVIDIA NIM, or
 * Hugging Face Inference) to correct one chunk of OCR text. Entirely opt-in: this class is only
 * ever constructed when the user has enabled online correction and supplied their own API key --
 * every other correction path in this app (rule-based, on-device LiteRT-LM) works fully offline.
 *
 * Gemini's generateContent REST shape and the OpenAI-compatible /chat/completions shape used by
 * OpenRouter and NVIDIA NIM are stable, publicly documented APIs. The Hugging Face Inference call
 * is more best-effort -- HF hosts models behind varying pipeline types (text-generation vs.
 * conversational), so the exact request/response shape can differ per model; if a given model
 * 404s or returns an unexpected shape, correction simply fails closed (chunk returned unmodified)
 * rather than crashing the pipeline.
 */
class OnlineLlmCorrector(
    private val provider: OnlineProvider,
    private val modelId: String,
    private val apiKey: String
) : LlmCorrector {

    override suspend fun correct(chunk: String): LlmResult = withContext(Dispatchers.IO) {
        runCatching {
            val rawOutput = when (provider) {
                OnlineProvider.GOOGLE_AI_STUDIO -> callGemini(chunk)
                OnlineProvider.HUGGING_FACE -> callHuggingFace(chunk)
                OnlineProvider.OPENROUTER, OnlineProvider.NVIDIA_NIM -> callOpenAiCompatible(chunk)
            }
            val cleaned = LlmOutputValidator.stripCodeFences(rawOutput)
            LlmOutputValidator.validate(chunk, cleaned)
        }.getOrElse { t ->
            LlmResult(text = chunk, accepted = false, rejectionReason = "Online correction failed: ${t.message}")
        }
    }

    override fun close() = Unit

    private fun callGemini(chunk: String): String {
        val url = URL("${provider.chatCompletionsUrl}/$modelId:generateContent?key=$apiKey")
        val body = JSONObject().apply {
            put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT)))
            )
            put(
                "contents",
                JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", chunk))))
            )
        }
        val response = JSONObject(postJson(url, body, headers = emptyMap()))
        return response.getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
            .getString("text")
    }

    private fun callOpenAiCompatible(chunk: String): String {
        val url = URL(provider.chatCompletionsUrl)
        val body = JSONObject().apply {
            put("model", modelId)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", chunk))
            )
            put("temperature", 0.0)
        }
        val response = JSONObject(postJson(url, body, headers = mapOf("Authorization" to "Bearer $apiKey")))
        return response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    private fun callHuggingFace(chunk: String): String {
        val url = URL("${provider.chatCompletionsUrl}/$modelId")
        val body = JSONObject().apply {
            put("inputs", "$SYSTEM_PROMPT\n\n$chunk")
            put("parameters", JSONObject().put("max_new_tokens", 2048).put("return_full_text", false))
        }
        val responseText = postJson(url, body, headers = mapOf("Authorization" to "Bearer $apiKey"))
        return if (responseText.trimStart().startsWith("[")) {
            JSONArray(responseText).getJSONObject(0).getString("generated_text")
        } else {
            JSONObject(responseText).getString("generated_text")
        }
    }

    private fun postJson(url: URL, body: JSONObject, headers: Map<String, String>): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()
        check(responseCode in 200..299) { "HTTP $responseCode: $text" }
        return text
    }
}
