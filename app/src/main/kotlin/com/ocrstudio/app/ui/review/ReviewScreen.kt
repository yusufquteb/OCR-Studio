package com.ocrstudio.app.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.ocrstudio.core.common.ReviewType
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.ui.components.ChipTone
import com.ocrstudio.core.ui.components.ConfidenceBadge
import com.ocrstudio.core.ui.components.EmptyState
import com.ocrstudio.core.ui.components.StatusChip
import com.ocrstudio.core.ui.components.TashkeelDiff
import com.ocrstudio.core.ui.components.WordDiffText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    val compareResults by viewModel.compareResults.collectAsState()
    val isComparing by viewModel.isComparing.collectAsState()
    val wordZoomBitmap by viewModel.wordZoomBitmap.collectAsState()

    var showCompareSheet by remember { mutableStateOf(false) }
    var compareSheetPage by remember { mutableStateOf<PageRecord?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    if (showCompareSheet && compareSheetPage != null) {
        ModalBottomSheet(
            onDismissRequest = { showCompareSheet = false; viewModel.clearCompareResults() },
            sheetState = sheetState
        ) {
            CompareSheet(
                page = compareSheetPage!!,
                results = compareResults,
                isLoading = isComparing,
                onUseResult = { text ->
                    viewModel.saveCorrection(compareSheetPage!!, text)
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        showCompareSheet = false
                        viewModel.clearCompareResults()
                    }
                }
            )
        }
    }

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
                        wordZoomBitmap = wordZoomBitmap,
                        onLoadImage = { viewModel.loadPageImage(page) },
                        correctionProviders = correctionProviders,
                        onSelectProvider = { entry -> viewModel.recorrectPage(page, entry) },
                        onSelectProviderWithType = { entry, type -> viewModel.recorrectWithType(page, entry, type) },
                        onCompareProviders = { entry ->
                            compareSheetPage = page
                            viewModel.compareProviders(page, correctionProviders)
                            showCompareSheet = true
                        },
                        onRecorrectChapter = { entry -> viewModel.recorrectChapter(pages, entry) },
                        onRecorrectBook = { entry -> viewModel.recorrectBook(entry) },
                        undoRedoState = undoRedoState[page.id] ?: UndoRedoState(),
                        onUndo = { viewModel.undo(page) },
                        onRedo = { viewModel.redo(page) },
                        onSave = { newText -> viewModel.saveCorrection(page, newText) },
                        onReprocess = { engineId, dpi -> viewModel.reprocessPage(page, engineId, dpi) },
                        onWordSelected = { word -> viewModel.cropWordBitmap(page, word) },
                        onClearWordZoom = { viewModel.clearWordZoom() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReviewPageCard(
    page: PageRecord,
    image: android.graphics.Bitmap?,
    wordZoomBitmap: android.graphics.Bitmap?,
    onLoadImage: () -> Unit,
    correctionProviders: List<CorrectionChainEntry>,
    onSelectProvider: (CorrectionChainEntry) -> Unit,
    onSelectProviderWithType: (CorrectionChainEntry, ReviewType) -> Unit,
    onCompareProviders: (CorrectionChainEntry) -> Unit,
    onRecorrectChapter: (CorrectionChainEntry) -> Unit,
    onRecorrectBook: (CorrectionChainEntry) -> Unit,
    undoRedoState: UndoRedoState,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: (String) -> Unit,
    onReprocess: (String, Int) -> Unit,
    onWordSelected: (String) -> Unit,
    onClearWordZoom: () -> Unit
) {
    var editedText by remember(page.id) { mutableStateOf(page.correctedText) }
    var menuExpanded by remember { mutableStateOf(false) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showImage by remember(page.id) { mutableStateOf(false) }
    var selectedTab by remember(page.id) { mutableIntStateOf(0) }
    var tashkeelWord by remember { mutableStateOf<String?>(null) }

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

            StatusChip(
                label = stringResource(
                    if (page.aiCorrectionApplied) R.string.review_ai_correction_applied
                    else R.string.review_ai_correction_not_applied
                ),
                tone = if (page.aiCorrectionApplied) ChipTone.SUCCESS else ChipTone.NEUTRAL,
                modifier = Modifier.padding(top = 4.dp)
            )

            if (page.tashkeelAiCompleted) {
                StatusChip(
                    label = stringResource(R.string.review_tashkeel_ai_completed),
                    tone = ChipTone.WARNING,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

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

            // Tab row: Arabic / Translation (only show Translation tab when translatedText exists)
            val tabs = buildList {
                add("Arabic")
                if (page.translatedText != null) add("Translation")
            }
            if (tabs.size > 1) {
                TabRow(selectedTabIndex = selectedTab, modifier = Modifier.padding(vertical = 4.dp)) {
                    tabs.forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title) })
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    Text(stringResource(R.string.review_raw_text), style = MaterialTheme.typography.labelMedium)
                    WordDiffText(rawText = page.rawText, correctedText = page.correctedText, modifier = Modifier.padding(bottom = 8.dp))

                    Text(stringResource(R.string.review_corrected_text), style = MaterialTheme.typography.labelMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        OutlinedTextField(
                            value = editedText,
                            onValueChange = { editedText = it },
                            textStyle = androidx.compose.ui.text.TextStyle.Default.copy(textDirection = TextDirection.Content),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (page.tashkeelAiCompleted) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            stringResource(R.string.review_tap_word_to_inspect),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        val correctedWords = remember(page.correctedText) {
                            page.correctedText.split(Regex("\\s+")).filter { it.isNotBlank() }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            correctedWords.forEach { word ->
                                FilterChip(
                                    selected = word == tashkeelWord,
                                    onClick = {
                                        if (tashkeelWord == word) {
                                            tashkeelWord = null
                                            onClearWordZoom()
                                        } else {
                                            tashkeelWord = word
                                            onWordSelected(word)
                                        }
                                    },
                                    label = { Text(word) }
                                )
                            }
                        }

                        if (tashkeelWord != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            wordZoomBitmap?.let { bmp ->
                                Text(
                                    stringResource(R.string.review_word_zoom_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = stringResource(R.string.review_word_zoom_label),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .padding(bottom = 8.dp)
                                )
                            }
                            val rawWords = page.rawText.split(Regex("\\s+")).filter { it.isNotBlank() }
                            val idx = correctedWords.indexOf(tashkeelWord)
                            if (idx >= 0) {
                                TashkeelDiff(
                                    rawWord = rawWords.getOrElse(idx) { "" },
                                    correctedWord = tashkeelWord!!,
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                )
                            }
                            TextButton(onClick = { tashkeelWord = null; onClearWordZoom() }) {
                                Text(stringResource(R.string.review_close))
                            }
                        }
                    }
                }
                1 -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(
                            page.translatedText ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // ReviewType chip row
            if (correctionProviders.isNotEmpty()) {
                Text(stringResource(R.string.review_type_label), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ReviewType.entries.forEach { type ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                correctionProviders.firstOrNull()?.let { entry ->
                                    onSelectProviderWithType(entry, type)
                                }
                            },
                            label = { Text(type.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
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
                        // Scope selector: This page / Chapter / Whole book
                        androidx.compose.foundation.layout.Box {
                            TextButton(onClick = { scopeMenuExpanded = true }) {
                                Text(stringResource(R.string.review_scope_label))
                            }
                            DropdownMenu(expanded = scopeMenuExpanded, onDismissRequest = { scopeMenuExpanded = false }) {
                                correctionProviders.firstOrNull()?.let { entry ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.review_scope_page)) },
                                        onClick = { scopeMenuExpanded = false; onSelectProvider(entry) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.review_scope_book)) },
                                        onClick = { scopeMenuExpanded = false; onRecorrectBook(entry) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.review_compare)) },
                                        onClick = { scopeMenuExpanded = false; onCompareProviders(entry) }
                                    )
                                }
                            }
                        }

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
private fun CompareSheet(
    page: PageRecord,
    results: List<CompareResult>,
    isLoading: Boolean,
    onUseResult: (String) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(stringResource(R.string.review_compare), style = MaterialTheme.typography.titleMedium)
        if (isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator()
            }
        } else if (results.isEmpty()) {
            Text(stringResource(R.string.review_compare_no_results), modifier = Modifier.padding(top = 8.dp))
        } else {
            results.forEach { result ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    "${result.entry.kind}: ${result.entry.modelId}",
                    style = MaterialTheme.typography.labelMedium
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    WordDiffText(
                        rawText = page.rawText,
                        correctedText = result.correctedText,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                Button(
                    onClick = { onUseResult(result.correctedText) },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(stringResource(R.string.review_compare_use_this))
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
