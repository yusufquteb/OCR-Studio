package com.ocrstudio.core.common

/** Static catalog of downloadable LiteRT-LM correction models shown on the Models screen. */
object LlmModelCatalog {
    val ALL: List<LlmModelInfo> = listOf(
        LlmModelInfo(
            id = "qwen3_0_6b",
            displayName = "Qwen3 0.6B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B_multi-prefill-seq_q8_ekv1280.litertlm",
            fileName = "qwen3-0.6b.litertlm",
            approxSizeMb = 700,
            ramRequirement = RamRequirement.LOW,
            licenseNote = "Qwen — Apache 2.0, no gate"
        ),
        LlmModelInfo(
            id = "qwen3_1_7b",
            displayName = "Qwen3 1.7B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-1.7B/resolve/main/Qwen3-1.7B_multi-prefill-seq_q8_ekv1280.litertlm",
            fileName = "qwen3-1.7b.litertlm",
            approxSizeMb = 1800,
            ramRequirement = RamRequirement.MEDIUM,
            licenseNote = "Qwen — Apache 2.0, no gate. Recommended quality/size balance."
        ),
        LlmModelInfo(
            id = "gemma_3n_e2b",
            displayName = "Gemma 3n E2B",
            downloadUrl = "https://huggingface.co/litert-community/gemma-3n-E2B-it/resolve/main/gemma-3n-E2B-it-int4.litertlm",
            fileName = "gemma-3n-e2b.litertlm",
            approxSizeMb = 3100,
            ramRequirement = RamRequirement.HIGH,
            licenseNote = "Gemma — requires accepting Google's Gemma terms on Hugging Face"
        ),
        LlmModelInfo(
            id = "gemma_4_e2b",
            displayName = "Gemma 4 E2B",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
            fileName = "gemma-4-e2b.litertlm",
            approxSizeMb = 3400,
            ramRequirement = RamRequirement.HIGH,
            licenseNote = "Gemma — requires accepting Google's Gemma terms on Hugging Face"
        )
    )

    fun availableFor(totalRamMb: Int): List<LlmModelInfo> =
        ALL.filter { it.ramRequirement.minRamMb <= totalRamMb }
}
