package com.ocrstudio.core.common

data class LlmResult(
    val text: String,
    val accepted: Boolean,
    val rejectionReason: String? = null
)

enum class RamRequirement(val minRamMb: Int) {
    LOW(3_000),
    MEDIUM(5_000),
    HIGH(8_000)
}

data class LlmModelInfo(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String,
    val approxSizeMb: Int,
    val ramRequirement: RamRequirement,
    val licenseNote: String
)
