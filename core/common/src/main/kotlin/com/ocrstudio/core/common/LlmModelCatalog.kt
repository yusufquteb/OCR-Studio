package com.ocrstudio.core.common

/**
 * Static catalog of downloadable LiteRT-LM correction models shown on the Models screen.
 *
 * HuggingFace is unreachable from this sandbox (network policy blocks it entirely), so these
 * URLs can only be checked indirectly via search-engine snapshots, and confirmed known-bad ones
 * are removed once a user reports the real HTTP status from a device that can reach HF:
 *   - "litert-community/Qwen3-8B" 404s on `Qwen3-8B.litertlm` (confirmed by a real download
 *     attempt) -- removed rather than guess another filename. litert-community's Qwen3 repos
 *     appear to ship per-chip files (e.g. a Qwen3-0.6B discussion thread references
 *     "Qwen3-0.6B.mediatek.mt6993.litertlm"), so the *0.6B* entry below may turn out to need the
 *     same treatment -- if it 404s too, use the Models screen's custom-URL/local-file fallback
 *     and report the exact filename that does exist so this catalog can be corrected.
 *   - gemma-3n-E2B-it-litert-lm is published under the "google" org, not "litert-community".
 *   - gemma-4-E2B-it-litert-lm only ships per-SoC files (Tensor G5 / Qualcomm / Intel) plus a
 *     "-web" build; there is no single generic-Android filename. The "-web" build is used here
 *     as the least hardware-locked option — if it underperforms on a given device, use the
 *     Models screen's "paste custom URL" fallback to pick a chip-specific file instead.
 *   - Gemma 3n E2B's HTTP 401 is expected, not a bug: it's a gated model and requires accepting
 *     Google's Gemma terms on the user's own Hugging Face account before it can be downloaded.
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
