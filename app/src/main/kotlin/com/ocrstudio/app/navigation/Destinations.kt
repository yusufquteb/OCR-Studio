package com.ocrstudio.app.navigation

sealed class Destination(val route: String) {
    data object Library : Destination("library")
    data object NewJobWizard : Destination("new_job_wizard")
    data object Search : Destination("search")
    data object Models : Destination("models")
    data object AiSettings : Destination("ai_settings")
    data object Export : Destination("export")
    data object Settings : Destination("settings")

    data object JobProgress : Destination("job_progress/{jobId}") {
        fun createRoute(jobId: String) = "job_progress/$jobId"
    }

    data object Review : Destination("review/{jobId}") {
        fun createRoute(jobId: String) = "review/$jobId"
    }
}

data class BottomNavItem(val destination: Destination, val labelRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)
