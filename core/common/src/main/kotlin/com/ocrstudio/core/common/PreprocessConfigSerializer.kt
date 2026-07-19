package com.ocrstudio.core.common

import org.json.JSONObject

/** Serializes PreprocessConfig to/from JSON so it can be persisted as a BookJob column. */
object PreprocessConfigSerializer {
    fun encode(config: PreprocessConfig): String = JSONObject().apply {
        put("grayscale", config.grayscale)
        put("deskew", config.deskew)
        put("cropToContent", config.cropToContent)
        put("denoise", config.denoise)
        put("denoiseH", config.denoiseH.toDouble())
        put("backgroundFlatten", config.backgroundFlatten)
        put("backgroundBlurKernel", config.backgroundBlurKernel)
        put("binarization", config.binarization)
        put("binarizationMethod", config.binarizationMethod.name)
        put("adaptiveBlockSize", config.adaptiveBlockSize)
        put("adaptiveC", config.adaptiveC)
        put("sauvolaWindowSize", config.sauvolaWindowSize)
        put("sauvolaK", config.sauvolaK)
        put("despeckle", config.despeckle)
        put("despeckleKernelSize", config.despeckleKernelSize)
    }.toString()

    fun decode(json: String): PreprocessConfig {
        val obj = JSONObject(json)
        return PreprocessConfig(
            grayscale = obj.optBoolean("grayscale", true),
            deskew = obj.optBoolean("deskew", true),
            cropToContent = obj.optBoolean("cropToContent", true),
            denoise = obj.optBoolean("denoise", true),
            denoiseH = obj.optDouble("denoiseH", 10.0).toFloat(),
            backgroundFlatten = obj.optBoolean("backgroundFlatten", true),
            backgroundBlurKernel = obj.optInt("backgroundBlurKernel", 51),
            binarization = obj.optBoolean("binarization", true),
            binarizationMethod = runCatching {
                BinarizationMethod.valueOf(obj.optString("binarizationMethod", BinarizationMethod.ADAPTIVE_GAUSSIAN.name))
            }.getOrDefault(BinarizationMethod.ADAPTIVE_GAUSSIAN),
            adaptiveBlockSize = obj.optInt("adaptiveBlockSize", 31),
            adaptiveC = obj.optDouble("adaptiveC", 15.0),
            sauvolaWindowSize = obj.optInt("sauvolaWindowSize", 25),
            sauvolaK = obj.optDouble("sauvolaK", 0.2),
            despeckle = obj.optBoolean("despeckle", true),
            despeckleKernelSize = obj.optInt("despeckleKernelSize", 2)
        )
    }
}
