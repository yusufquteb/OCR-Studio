package com.ocrstudio.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ocrstudio.app.R
import com.ocrstudio.app.ui.export.ExportScreen
import com.ocrstudio.app.ui.library.LibraryScreen
import com.ocrstudio.app.ui.models.ModelsScreen
import com.ocrstudio.app.ui.newjob.NewJobWizardScreen
import com.ocrstudio.app.ui.progress.JobProgressScreen
import com.ocrstudio.app.ui.review.ReviewScreen
import com.ocrstudio.app.ui.search.SearchScreen
import com.ocrstudio.app.ui.settings.SettingsScreen

private val bottomNavItems = listOf(
    BottomNavItem(Destination.Library, R.string.nav_library, Icons.Filled.LibraryBooks),
    BottomNavItem(Destination.Search, R.string.nav_search, Icons.Filled.Search),
    BottomNavItem(Destination.Models, R.string.nav_models, Icons.Filled.CloudDownload),
    BottomNavItem(Destination.Export, R.string.export_title, Icons.Filled.Upload),
    BottomNavItem(Destination.Settings, R.string.nav_settings, Icons.Filled.Settings)
)

@Composable
fun OcrStudioNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = { OcrStudioBottomBar(navController) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Library.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Destination.Library.route) {
                LibraryScreen(
                    onAddPdf = { navController.navigate(Destination.NewJobWizard.route) },
                    onOpenJob = { jobId -> navController.navigate(Destination.JobProgress.createRoute(jobId)) }
                )
            }
            composable(Destination.NewJobWizard.route) {
                NewJobWizardScreen(
                    onDone = { jobId ->
                        navController.popBackStack()
                        navController.navigate(Destination.JobProgress.createRoute(jobId))
                    },
                    onNavigateToModels = { navController.navigate(Destination.Models.route) }
                )
            }
            composable(Destination.JobProgress.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
                JobProgressScreen(
                    jobId = jobId,
                    onOpenReview = { navController.navigate(Destination.Review.createRoute(jobId)) },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Destination.Review.route) { backStackEntry ->
                val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
                ReviewScreen(jobId = jobId, onBack = { navController.popBackStack() })
            }
            composable(Destination.Search.route) { SearchScreen() }
            composable(Destination.Models.route) { ModelsScreen() }
            composable(Destination.Export.route) { ExportScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun OcrStudioBottomBar(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.destination.route,
                onClick = {
                    navController.navigate(item.destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResourceCompat(item.labelRes)) }
            )
        }
    }
}

@Composable
private fun stringResourceCompat(resId: Int): String = androidx.compose.ui.res.stringResource(resId)
