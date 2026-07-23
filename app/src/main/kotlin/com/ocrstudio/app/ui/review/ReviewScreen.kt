package com.ocrstudio.app.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.CorrectionChainEntry
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.ui.components.ChipTone
import com.ocrstudio.core.ui.components.ConfidenceBadge
import com.ocrstudio.core.ui.components.EmptyState
import com.ocrstudio.core.ui.components.StatusChip
import com.ocrstudio.core.ui.components.WordDiffText

@Composable
fun ReviewScreen(
    jobId: String,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(
        creationCallback = { factory: ReviewViewModel.Factory -> factory.create(jobId) }
    )
) {
    val pages by viewModel.pagesNeedingReview.collectAsState()
    val correctionProviders by viewModel.correctionProviders.collectAsState()
    val pageImages by viewModel.pageImages.collectAsState()
    val undoRedoState by viewModel.undoRedoState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.review_title)) }) }) { padding ->
        if (pages.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.CheckCircle,
                title = "Nothing needs review",
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(pages, key = { it.id }) { page ->
                    ReviewPageCard(
                        page = page,
                        image = pageImages[page.id],
                        onLoadImage = { viewModel.loadPageImage(page) },
                        correctionProviders = correctionProviders,
                        onSelectProvider = { entry -> viewModel.recorrectPage(page, entry) },
                        undoRedoState = undoRedoState[page.id] ?: UndoRedoState(),
                        onUndo = { viewModel.undo(page) },
                        onRedo = { viewModel.redo(page) },
                        onSave = { newText -> viewModel.saveCorrection(page, newText) },
                        onReprocess = { engineId, dpi -> viewModel.reprocessPage(page, engineId, dpi) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewPageCard(
    page: PageRecord,
    image: android.graphics.Bitmap?,
    onLoadImage: () -> Unit,
    correctionProviders: List<CorrectionChainEntry>,
    onSelectProvider: (CorrectionChainEntry) -> Unit,
    undoRedoState: UndoRedoState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (String) -> Unit,
    onReprocess: (String, Int) -> Unit
) {
    var editedText by remember(page.id) { mutableStateOf(page.correctedText) }
    var menuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var showImage by remember(page.id) { mutableStateOf(false) }

    LaunchedEffect(showImage) {
        if (showImage) onLoadImage()
    }

    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Page ${page.pageNumber}", style = MaterialTheme.typography.titleMedium)
                ConfidenceBadge(page.finalScore)
            }

            // Whether an LLM corrector actually accepted a rewrite for this page, so a
            // configured correction chain that silently never fires (e.g. every entry rejected,
            // or an offline-only chain in a build without on-device support) is visible here
            // instead of looking indistinguishable from working correction.
            StatusChip(
                label = stringResource(
                    if (page.aiCorrectionApplied) R.string.review_ai_correction_applied
                    else R.string.review_ai_correction_not_applied
                ),
                tone = if (page.aiCorrectionApplied) ChipTone.SUCCESS else ChipTone.NEUTRAL,
                modifier = Modifier.padding(top = 4.dp)
            )

            TextButton(onClick = { showImage = !showImage }, modifier = Modifier.padding(top = 4.dp)) {
                Text(if (showImage) "Hide original page" else "Compare with original page")
            }
            if (showImage) {
                if (image != null) {
                    ZoomableImage(
                        bitmap = image,
                        modifier = Modifier.fillMaxWidth().height(320.dp).padding(bottom = 8.dp)
                    )
                } else {
                    Text("Rendering page…", style = MaterialTheme.typography.bodySmall)
                }
            }

            Text(stringResource(R.string.review_raw_text), style = MaterialTheme.typography.labelMedium)
            WordDiffText(rawText = page.rawText, correctedText = page.correctedText, modifier = Modifier.padding(bottom = 8.dp))

            Text(stringResource(R.string.review_corrected_text), style = MaterialTheme.typography.labelMedium)
            // Rtl LocalLayoutDirection (not just textDirection = Content) is needed here too --
            // otherwise the field's default TextAlign.Start still resolves to left under the
            // app's ambient LTR direction, so wrapped Arabic paragraphs and the cursor's resting
            // position both stay on the wrong side.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    textStyle = androidx.compose.ui.text.TextStyle.Default.copy(textDirection = TextDirection.Content),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row {
                    Button(onClick = { onSave(editedText) }) { Text(stringResource(R.string.review_save)) }
                    IconButton(onClick = onUndo, enabled = undoRedoState.canUndo) {
                        Icon(Icons.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = onRedo, enabled = undoRedoState.canRedo) {
                        Icon(Icons.Filled.Redo, contentDescription = "Redo")
                    }
                }

                Row {
                    if (correctionProviders.isNotEmpty()) {
                        androidx.compose.foundation.layout.Box {
                            TextButton(onClick = { providerMenuExpanded = true }) { Text("AI provider") }
                            DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                                correctionProviders.forEach { entry ->
                                    DropdownMenuItem(
                                        text = { Text("${entry.kind}: ${entry.modelId}") },
                                        onClick = { providerMenuExpanded = false; onSelectProvider(entry) }
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { menuExpanded = true }) { Text(stringResource(R.string.review_reprocess_page)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Retry with Tesseract @ 400 DPI") },
                                onClick = { menuExpanded = false; onReprocess(OcrEngineIds.TESSERACT, 400) }
                            )
                            DropdownMenuItem(
                                text = { Text("Retry with PaddleOCR @ current DPI") },
                                onClick = { menuExpanded = false; onReprocess(OcrEngineIds.PADDLE, 300) }
                            )
                            DropdownMenuItem(
                                text = { Text("Retry with Tesseract @ 600 DPI") },
                                onClick = { menuExpanded = false; onReprocess(OcrEngineIds.TESSERACT, 600) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableImage(bitmap: android.graphics.Bitmap, modifier: Modifier = Modifier) {
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offsetX,
                translationY = offsetY
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    )
}
