package com.ocrstudio.app.ui.newjob

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Ephemeral in-memory hand-off of the SAF PDF Uri picked on Library -> New Job Wizard. */
@Singleton
class NewJobDraftHolder @Inject constructor() {
    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri

    private val _pdfDisplayName = MutableStateFlow<String?>(null)
    val pdfDisplayName: StateFlow<String?> = _pdfDisplayName

    fun set(uri: Uri, displayName: String?) {
        _pdfUri.value = uri
        _pdfDisplayName.value = displayName
    }

    fun clear() {
        _pdfUri.value = null
        _pdfDisplayName.value = null
    }
}
