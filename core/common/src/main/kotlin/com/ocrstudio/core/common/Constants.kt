package com.ocrstudio.core.common

object OcrEngineIds {
    const val TESSERACT = "tesseract"
    const val PADDLE = "paddle_ocr"
}

object ParserProfileIds {
    const val GENERIC = "generic"
    const val MUJAM_MUFAHRAS = "mujam_mufahras"
    const val HADITH = "hadith"
    const val TAFSIR = "tafsir"
}

object CorrectorIds {
    const val NONE = "none"
    const val LITE_RT_LM = "litert_lm"
}

object AssetPaths {
    const val TESSDATA_DIR = "tessdata"
    const val TESSERACT_ARA_FILE = "ara.traineddata"
    const val PADDLE_DIR = "paddle"
    const val PADDLE_DET_FILE = "det.onnx"
    const val PADDLE_REC_AR_FILE = "rec_ar.onnx"
    const val PADDLE_DICT_AR_FILE = "dict_ar.txt"
    const val LLM_MODELS_DIR = "llm_models"
    const val REFERENCE_DICT_DIR = "ref_dicts"
}

data class ReferenceDictionaryInfo(
    val id: String,
    val displayName: String,
    val downloadUrl: String,
    val fileName: String,
    val approxSizeKb: Int,
    val domain: String,
    val licenseNote: String
)

object ReferenceDictionaryCatalog {
    val ALL: List<ReferenceDictionaryInfo> = listOf(
        ReferenceDictionaryInfo(
            id = "quran_tashkeel",
            displayName = "Qur'an vocabulary",
            downloadUrl = "https://github.com/yusufquteb/ocr-ref-dicts/releases/download/v1/quran_tashkeel.dict",
            fileName = "quran_tashkeel.dict",
            approxSizeKb = 800,
            domain = "quran",
            licenseNote = "Public domain"
        ),
        ReferenceDictionaryInfo(
            id = "hadith_terms",
            displayName = "Hadith terminology",
            downloadUrl = "https://github.com/yusufquteb/ocr-ref-dicts/releases/download/v1/hadith_terms.dict",
            fileName = "hadith_terms.dict",
            approxSizeKb = 600,
            domain = "hadith",
            licenseNote = "Public domain"
        ),
        ReferenceDictionaryInfo(
            id = "fiqh_terms",
            displayName = "Fiqh terminology",
            downloadUrl = "https://github.com/yusufquteb/ocr-ref-dicts/releases/download/v1/fiqh_terms.dict",
            fileName = "fiqh_terms.dict",
            approxSizeKb = 400,
            domain = "fiqh",
            licenseNote = "Public domain"
        )
    )

    fun byId(id: String): ReferenceDictionaryInfo? = ALL.find { it.id == id }
    fun forDomain(domain: String): List<ReferenceDictionaryInfo> = ALL.filter { it.domain == domain }
}

object DownloadUrls {
    const val TESSERACT_ARA =
        "https://github.com/tesseract-ocr/tessdata_best/raw/main/ara.traineddata"
    const val PADDLE_DET =
        "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/detection/v3/det.onnx"
    const val PADDLE_REC_AR =
        "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/arabic/rec.onnx"
    const val PADDLE_DICT_AR =
        "https://huggingface.co/monkt/paddleocr-onnx/resolve/main/languages/arabic/dict.txt"
}
