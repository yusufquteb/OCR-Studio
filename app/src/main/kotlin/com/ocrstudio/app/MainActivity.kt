package com.ocrstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ocrstudio.app.navigation.OcrStudioNavHost
import com.ocrstudio.core.ui.theme.OcrStudioTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OcrStudioTheme {
                OcrStudioNavHost()
            }
        }
    }
}
