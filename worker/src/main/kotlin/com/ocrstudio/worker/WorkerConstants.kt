package com.ocrstudio.worker

object WorkerConstants {
    const val KEY_JOB_ID = "job_id"
    const val KEY_START_PAGE = "start_page"
    const val KEY_END_PAGE = "end_page"
    const val KEY_BATCH_INDEX = "batch_index"
    const val KEY_BATCH_COUNT = "batch_count"

    const val NOTIFICATION_CHANNEL_ID = "ocr_processing"
    const val NOTIFICATION_ID_BASE = 5000

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "asset_download"
    const val DOWNLOAD_NOTIFICATION_ID = 6000

    const val KEY_DOWNLOAD_URL = "download_url"
    const val KEY_DOWNLOAD_DEST_PATH = "download_dest_path"
    const val KEY_DOWNLOAD_LABEL = "download_label"

    const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
    const val KEY_TOTAL_BYTES = "total_bytes"
    const val KEY_PROGRESS_PERCENT = "progress_percent"
}
