package com.ocrstudio.core.database

import androidx.room.TypeConverter
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.entity.ErrorStage

class Converters {
    @TypeConverter
    fun fromJobStatus(value: JobStatus): String = value.name

    @TypeConverter
    fun toJobStatus(value: String): JobStatus = JobStatus.valueOf(value)

    @TypeConverter
    fun fromErrorStage(value: ErrorStage): String = value.name

    @TypeConverter
    fun toErrorStage(value: String): ErrorStage = ErrorStage.valueOf(value)

    @TypeConverter
    fun fromExportFormat(value: ExportFormat): String = value.name

    @TypeConverter
    fun toExportFormat(value: String): ExportFormat = ExportFormat.valueOf(value)
}
