package com.ocrstudio.app.ui.export

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.ExportRecordDao
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.core.database.entity.ExportRecord
import com.ocrstudio.engine.export.ExportRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val bookJobDao: BookJobDao,
    private val exportRecordDao: ExportRecordDao,
    private val exportRegistry: ExportRegistry
) : ViewModel() {

    val jobs: StateFlow<List<BookJob>> = bookJobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history: StateFlow<List<ExportRecord>> = exportRecordDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun export(jobId: String, format: ExportFormat, destination: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val plugin = exportRegistry.pluginFor(format)
            val pageCount = plugin.export(jobId, destination)
            exportRecordDao.insert(
                ExportRecord(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    format = format,
                    destinationUri = destination.toString(),
                    pageCount = pageCount,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
            onDone(pageCount)
        }
    }
}
