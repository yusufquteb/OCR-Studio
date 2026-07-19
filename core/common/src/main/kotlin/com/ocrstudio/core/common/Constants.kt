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
