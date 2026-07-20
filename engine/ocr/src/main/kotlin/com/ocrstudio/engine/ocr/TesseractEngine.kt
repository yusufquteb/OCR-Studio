package com.ocrstudio.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.googlecode.tesseract.android.ResultIterator
import com.googlecode.tesseract.android.TessBaseAPI
import com.googlecode.tesseract.android.TessBaseAPI.PageIteratorLevel
import com.ocrstudio.core.common.AssetPaths
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.OcrWord
import com.ocrstudio.core.common.PageSegmentationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * The guaranteed OCR path: language "ara", OEM_LSTM_ONLY, PSM auto (or single-column for
 * dictionary-style books). Per-word confidence extracted via ResultIterator. One TessBaseAPI
 * instance is meant to be reused for an entire batch (init once, close() at batch end).
 */
class TesseractEngine @Inject constructor() : OcrEngine {

    override val id: String = OcrEngineIds.TESSERACT

    private var baseApi: TessBaseAPI? = null

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        File(context.filesDir, "${AssetPaths.TESSDATA_DIR}/${AssetPaths.TESSERACT_ARA_FILE}").exists()
    }

    override suspend fun init(context: Context, config: OcrConfig) = withContext(Dispatchers.Default) {
        val api = TessBaseAPI()
        val psm = when (config.psm) {
            PageSegmentationMode.SINGLE_COLUMN -> TessBaseAPI.PageSegMode.PSM_SINGLE_COLUMN
            PageSegmentationMode.SINGLE_BLOCK -> TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK
            PageSegmentationMode.AUTO -> TessBaseAPI.PageSegMode.PSM_AUTO
        }
        val initialized = api.init(config.dataDir, config.language, TessBaseAPI.OEM_LSTM_ONLY)
        check(initialized) { "Failed to initialize Tesseract with language=${config.language} dataDir=${config.dataDir}" }
        api.pageSegMode = psm
        baseApi = api
    }

    override suspend fun recognize(bitmap: Bitmap): OcrPage = withContext(Dispatchers.Default) {
        val api = checkNotNull(baseApi) { "TesseractEngine.init() must be called before recognize()" }
        api.setImage(bitmap)
        val text = api.utF8Text ?: ""
        val words = extractWords(api)
        val meanConfidence = if (words.isNotEmpty()) {
            words.map { it.confidence }.average().toFloat()
        } else {
            (api.meanConfidence() / 100f)
        }
        api.clear()
        OcrPage(text = text, words = words, meanConfidence = meanConfidence, engineId = id)
    }

    private fun extractWords(api: TessBaseAPI): List<OcrWord> {
        val words = mutableListOf<OcrWord>()
        val iterator: ResultIterator = api.resultIterator ?: return emptyList()
        try {
            do {
                val wordText = iterator.getUTF8Text(PageIteratorLevel.RIL_WORD) ?: continue
                if (wordText.isBlank()) continue
                val confidence = iterator.confidence(PageIteratorLevel.RIL_WORD) / 100f
                val box: Rect? = iterator.getBoundingRect(PageIteratorLevel.RIL_WORD)
                words.add(
                    OcrWord(
                        text = wordText,
                        confidence = confidence,
                        left = box?.left ?: 0,
                        top = box?.top ?: 0,
                        right = box?.right ?: 0,
                        bottom = box?.bottom ?: 0
                    )
                )
            } while (iterator.next(PageIteratorLevel.RIL_WORD))
        } finally {
            iterator.delete()
        }
        return words
    }

    override fun close() {
        baseApi?.recycle()
        baseApi = null
    }
}
