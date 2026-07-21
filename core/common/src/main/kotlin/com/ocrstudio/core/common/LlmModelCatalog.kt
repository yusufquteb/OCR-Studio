package com.ocrstudio.core.common

/**
 * Static catalog of downloadable LiteRT-LM correction models shown on the Models screen.
 *
 * URLs were re-verified (2026-07) against each repo's actual file listing (HuggingFace itself
 * isn't reachable from every build/dev environment, so this was done via search-engine-indexed
 * page snapshots of the exact blob URLs, which is strong evidence the files exist, though not
 * a substitute for an app-side HTTP HEAD check). Two problems were found and fixed vs. the
 * original spec:
 *   - "litert-community/Qwen3-1.7B" does not appear to exist at all; replaced with the
 *     confirmed-real litert-community/Qwen3-8B repo (bumped to the HIGH RAM tier accordingly).
 *   - Qwen3-0.6B's actual filename is "Qwen3-0.6B.litertlm", not the guessed
 *     "*_multi-prefill-seq_q8_ekv1280.litertlm".
 *   - gemma-3n-E2B-it-litert-lm is published under the "google" org, not "litert-community".
 *   - gemma-4-E2B-it-litert-lm only ships per-SoC files (Tensor G5 / Qualcomm / Intel) plus a
 *     "-web" build; there is no single generic-Android filename. The "-web" build is used here
 *     as the least hardware-locked option — if it underperforms on a given device, use the
 *     Models screen's "paste custom URL" fallback to pick a chip-specific file instead.
 *
 * All of these are optional downloads behind AssetDownloadManager, which already handles 404s
 * gracefully with a custom-URL / local-file-import fallback, so a wrong URL here never crashes
 * the app -- it just means users need that fallback until this catalog is corrected.
 */
object LlmModelCatalog {
    val ALL: List<LlmModelInfo> = listOf(
        LlmModelInfo(
            id = "qwen3_0_6b",
            displayName = "Qwen3 0.6B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm",
            fileName = "qwen3-0.6b.litertlm",
            approxSizeMb = 700,
            ramRequirement = RamRequirement.LOW,
            licenseNote = "Qwen — Apache 2.0, no gate"
        ),
        LlmModelInfo(
            id = "qwen3_8b",
            displayName = "Qwen3 8B",
            downloadUrl = "https://huggingface.co/litert-community/Qwen3-8B/resolve/main/Qwen3-8B.litertlm",
            fileName = "qwen3-8b.litertlm",
            approxSizeMb = 8500,
            ramRequirement = RamRequirement.HIGH,
            licenseNote = "Qwen — Apache 2.0, no gate. Highest quality of the Qwen options."
        ),
        LlmModelInfo(
            id = "gemma_3n_e2b",
            displayName = "Gemma 3n E2B",
            downloadUrl = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm",
            fileName = "gemma-3n-e2b.litertlm",
            approxSizeMb = 3660,
            ramRequirement = RamRequirement.HIGH,
            licenseNote = "Gemma — requires accepting Google's Gemma terms on Hugging Face"
        ),
        LlmModelInfo(
            id = "gemma_4_e2b",
            displayName = "Gemma 4 E2B",
            downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-web.litertlm",
            fileName = "gemma-4-e2b.litertlm",
            approxSizeMb = 2010,
            ramRequirement = RamRequirement.HIGH,
            licenseNote = "Gemma — requires accepting Google's Gemma terms on Hugging Face. " +
                "Generic (\"-web\") build; if it underperforms, use a chip-specific file via " +
                "the custom-URL import option."
        )
    )

    fun availableFor(totalRamMb: Int): List<LlmModelInfo> =
        ALL.filter { it.ramRequirement.minRamMb <= totalRamMb }
}
