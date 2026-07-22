package com.ocrstudio.core.common

const val MAX_CORRECTION_CHAIN_SIZE = 4

enum class CorrectorKind { OFFLINE, ONLINE }

/** One entry in the correction fallback chain. [modelId] is an LlmModelInfo.id for OFFLINE,
 *  an OnlineModelInfo.id for ONLINE. */
data class CorrectionChainEntry(val kind: CorrectorKind, val modelId: String)
