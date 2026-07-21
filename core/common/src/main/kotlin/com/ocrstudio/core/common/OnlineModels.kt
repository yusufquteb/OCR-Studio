package com.ocrstudio.core.common

/**
 * Optional online correction providers. Unlike everything else in this app, these require
 * network access and a user-supplied API key -- off by default, opt-in only, never required
 * for the app to function (rule-based correction and the offline LiteRT-LM path both still
 * work with no network at all).
 */
enum class OnlineProvider(val displayName: String, val chatCompletionsUrl: String) {
    GOOGLE_AI_STUDIO("Google AI Studio (Gemini)", "https://generativelanguage.googleapis.com/v1beta/models"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/chat/completions"),
    NVIDIA_NIM("NVIDIA NIM", "https://integrate.api.nvidia.com/v1/chat/completions"),
    HUGGING_FACE("Hugging Face Inference", "https://api-inference.huggingface.co/models")
}

data class OnlineModelInfo(
    val id: String,
    val provider: OnlineProvider,
    /** The exact model identifier the provider's API expects. */
    val modelId: String,
    val displayName: String,
    val note: String
)

/**
 * Curated to Arabic-capable models only, one representative pick per provider plus a couple of
 * standouts (Jais is a model trained specifically for Arabic; the rest are strong multilingual
 * models with well-documented Arabic support). Exact model IDs can change on the provider's end
 * -- if one 404s, use the Models screen's custom-model-id override for that provider.
 */
object OnlineModelCatalog {
    val ALL: List<OnlineModelInfo> = listOf(
        OnlineModelInfo(
            id = "gemini_flash",
            provider = OnlineProvider.GOOGLE_AI_STUDIO,
            modelId = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            note = "سريع ورخيص، دعم عربي قوي"
        ),
        OnlineModelInfo(
            id = "gemini_pro",
            provider = OnlineProvider.GOOGLE_AI_STUDIO,
            modelId = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            note = "أعلى جودة وأبطأ، للنصوص الصعبة (معاجم/فهارس)"
        ),
        OnlineModelInfo(
            id = "jais_30b",
            provider = OnlineProvider.HUGGING_FACE,
            modelId = "inceptionai/jais-30b-chat-v3",
            displayName = "Jais 30B (متخصص بالعربية)",
            note = "نموذج G42/Inception المطوّر خصيصًا للغة العربية"
        ),
        OnlineModelInfo(
            id = "qwen_hf",
            provider = OnlineProvider.HUGGING_FACE,
            modelId = "Qwen/Qwen2.5-7B-Instruct",
            displayName = "Qwen2.5 7B (عبر Hugging Face)",
            note = "دعم عربي جيد، حجم أصغر وأسرع"
        ),
        OnlineModelInfo(
            id = "qwen_openrouter",
            provider = OnlineProvider.OPENROUTER,
            modelId = "qwen/qwen-2.5-72b-instruct",
            displayName = "Qwen2.5 72B (عبر OpenRouter)",
            note = "دعم عربي قوي، نموذج أكبر وأدق"
        ),
        OnlineModelInfo(
            id = "deepseek_openrouter",
            provider = OnlineProvider.OPENROUTER,
            modelId = "deepseek/deepseek-chat",
            displayName = "DeepSeek V3 (عبر OpenRouter)",
            note = "دعم عربي جيد بتكلفة منخفضة"
        ),
        OnlineModelInfo(
            id = "nvidia_llama",
            provider = OnlineProvider.NVIDIA_NIM,
            modelId = "meta/llama-3.1-70b-instruct",
            displayName = "Llama 3.1 70B (عبر NVIDIA NIM)",
            note = "دعم عربي معقول، استضافة NVIDIA"
        )
    )

    fun byId(id: String): OnlineModelInfo? = ALL.find { it.id == id }
    fun forProvider(provider: OnlineProvider): List<OnlineModelInfo> = ALL.filter { it.provider == provider }
}

/** Persisted online-correction configuration; disabled unless [enabled] and [apiKey] are both set. */
data class OnlineCorrectionConfig(
    val enabled: Boolean = false,
    val modelId: String? = null,
    val apiKey: String = ""
) {
    val isUsable: Boolean get() = enabled && modelId != null && apiKey.isNotBlank()
}
