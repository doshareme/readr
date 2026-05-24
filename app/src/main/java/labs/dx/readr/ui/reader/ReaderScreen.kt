package labs.dx.readr.ui.reader

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NavigateBefore
import androidx.compose.material.icons.outlined.NavigateNext
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import labs.dx.core.domain.model.HighlightMode
import labs.dx.core.domain.model.PdfPageLayoutDebugInfo
import labs.dx.core.domain.model.PdfPageReadingMode
import labs.dx.core.domain.model.PdfPageInfo
import labs.dx.core.domain.model.PdfWord
import labs.dx.core.domain.model.PlaybackState
import labs.dx.core.domain.model.ReaderError
import labs.dx.readr.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pageSizes = remember { mutableStateMapOf<Int, IntSize>() }
    var suppressAutoScroll by remember { mutableStateOf(false) }
    var hasClearedResources by remember { mutableStateOf(false) }
    var hasFocusedInitialPlayback by remember(state.document?.documentId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val bottomSheetScaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val controlsExpanded = bottomSheetState.currentValue == SheetValue.Expanded ||
        bottomSheetState.targetValue == SheetValue.Expanded

    fun exitReader() {
        if (!hasClearedResources) {
            hasClearedResources = true
            viewModel.clearReaderMemory()
        }
        onNavigateBack()
    }

    BackHandler {
        exitReader()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!hasClearedResources) {
                hasClearedResources = true
                viewModel.clearReaderMemory()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // no-op hook keeps the screen lifecycle-aware and ready for future pause policy changes
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            suppressAutoScroll = true
        } else {
            kotlinx.coroutines.delay(1200)
            suppressAutoScroll = false
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        Toast.makeText(context, error.toMessage(), Toast.LENGTH_LONG).show()
        snackbarHostState.showSnackbar(error.toMessage())
    }

    LaunchedEffect(state.playbackState) {
        val error = (state.playbackState as? PlaybackState.Error)?.error ?: return@LaunchedEffect
        Toast.makeText(context, error.toMessage(), Toast.LENGTH_LONG).show()
        snackbarHostState.showSnackbar(error.toMessage())
    }

    LaunchedEffect(state.currentHighlight?.word?.globalWordIndex, state.playbackState, suppressAutoScroll) {
        val currentWord = state.currentHighlight?.word ?: return@LaunchedEffect
        val playbackState = state.playbackState
        val shouldFocus = !hasFocusedInitialPlayback &&
            !suppressAutoScroll &&
            (playbackState is PlaybackState.Preparing || playbackState is PlaybackState.Playing)
        if (!shouldFocus) return@LaunchedEffect
        val pageSize = pageSizes[currentWord.pageIndex]
        if (pageSize == null) {
            listState.scrollToItem(index = currentWord.pageIndex)
            hasFocusedInitialPlayback = true
            return@LaunchedEffect
        }
        val pageHeight = pageSize.height
        val pageInfo = viewModel.loadPageInfo(currentWord.pageIndex) ?: return@LaunchedEffect
        val wordOffset = ((currentWord.boundingBox.top / pageInfo.heightPoints.toFloat()) * pageHeight.toFloat()).toInt()
        listState.scrollToItem(
            index = currentWord.pageIndex,
            scrollOffset = (wordOffset - 96).coerceAtLeast(0)
        )
        hasFocusedInitialPlayback = true
    }

    BottomSheetScaffold(
        scaffoldState = bottomSheetScaffoldState,
        topBar = {
            LowPowerBlurChrome(shape = RectangleShape) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    title = {
                        Text(state.document?.displayName ?: "Readr")
                    },
                    navigationIcon = {
                        IconButton(onClick = ::exitReader) {
                            Icon(Icons.Outlined.NavigateBefore, contentDescription = "Back")
                        }
                    }
                )
            }
        },
        sheetPeekHeight = 72.dp,
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
        sheetContent = {
            LowPowerBlurChrome(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    SheetPeekHeader(
                        expanded = controlsExpanded,
                        onExpand = {
                            scope.launch { bottomSheetState.expand() }
                        }
                    )
                    if (controlsExpanded && state.documentInfo?.hasExtractableText == false) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Preview only",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "This document does not expose text for narration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (controlsExpanded) {
                        Spacer(Modifier.height(12.dp))
                        ReaderControls(
                            state = state,
                            onPlayPause = viewModel::onPlayPauseTapped,
                            onStop = viewModel::onStopTapped,
                            onPreviousSentence = { viewModel.onSeekSentence(-1) },
                            onNextSentence = { viewModel.onSeekSentence(1) },
                            onPreviousParagraph = { viewModel.onSeekParagraph(-1) },
                            onNextParagraph = { viewModel.onSeekParagraph(1) },
                            onRateChange = viewModel::updateSpeechRate,
                            onVoiceChange = viewModel::updateVoice,
                            onCloudTtsChanged = viewModel::updateCloudTts,
                            onResearchPaperModeChanged = viewModel::updateResearchPaperMode,
                            onStoryModeChanged = viewModel::updateStoryMode,
                            onSummarize = viewModel::onSummarizeTapped,
                            onPlaySummary = viewModel::onPlaySummaryTapped,
                            onStopSummary = viewModel::onStopSummaryTapped,
                            onHideControls = {
                                scope.launch { bottomSheetState.partialExpand() }
                            }
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when {
            state.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            state.documentInfo == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.error?.toMessage() ?: "Unable to open document")
                }
            }

            else -> {
                val documentInfo = requireNotNull(state.documentInfo)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items((0 until documentInfo.pageCount).toList(), key = { it }) { pageIndex ->
                        ReaderPage(
                            pageIndex = pageIndex,
                            currentWord = state.currentHighlight?.word,
                            currentMode = state.currentHighlight?.mode,
                            showResearchDebugOverlay = BuildConfig.DEBUG && state.settings.researchPaperMode,
                            onWordDoubleTapped = viewModel::onWordDoubleTapped,
                            onSizeChanged = { pageSizes[pageIndex] = it },
                            loadPageInfo = { viewModel.loadPageInfo(pageIndex) },
                            loadPageLayoutDebugInfo = { viewModel.loadPageLayoutDebugInfo(pageIndex) },
                            loadBitmap = { width -> viewModel.loadPageBitmap(pageIndex, width) }
                        )
                    }
                    item {
                        Spacer(Modifier.height(88.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetPeekHeader(
    expanded: Boolean,
    onExpand: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(50)
                )
        )
        Spacer(Modifier.height(8.dp))
        if (expanded) {
            Text(
                text = "Playback Controls",
                style = MaterialTheme.typography.titleSmall
            )
        } else {
            TextButton(onClick = onExpand) {
                Text("Swipe up or tap to show controls")
            }
        }
    }
}

@Composable
private fun LowPowerBlurChrome(
    modifier: Modifier = Modifier,
    shape: Shape,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val baseColor = if (darkTheme) {
        Color(0xFF2B2B2F).copy(alpha = 0.84f)
    } else {
        Color.White.copy(alpha = 0.82f)
    }
    val borderColor = if (darkTheme) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color(0xFF111827).copy(alpha = 0.08f)
    }
    val highlightColor = if (darkTheme) {
        Color.White.copy(alpha = 0.04f)
    } else {
        Color.White.copy(alpha = 0.22f)
    }

    Box(
        modifier = modifier
            .background(baseColor, shape)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(highlightColor, Color.Transparent)
                ),
                shape = shape
            )
    ) {
        content()
    }
}

@Composable
private fun ReaderPage(
    pageIndex: Int,
    currentWord: PdfWord?,
    currentMode: HighlightMode?,
    showResearchDebugOverlay: Boolean,
    onWordDoubleTapped: (pageIndex: Int, normalizedTapX: Float, normalizedTapY: Float) -> Unit,
    onSizeChanged: (IntSize) -> Unit,
    loadPageInfo: suspend () -> PdfPageInfo?,
    loadPageLayoutDebugInfo: suspend () -> PdfPageLayoutDebugInfo?,
    loadBitmap: suspend (Int) -> Bitmap?
) {
    var containerWidth by remember { mutableStateOf(1) }
    var bitmap by remember(pageIndex, containerWidth) { mutableStateOf<Bitmap?>(null) }
    var pageInfo by remember(pageIndex) { mutableStateOf<PdfPageInfo?>(null) }
    var layoutDebugInfo by remember(pageIndex, showResearchDebugOverlay) { mutableStateOf<PdfPageLayoutDebugInfo?>(null) }

    LaunchedEffect(pageIndex) {
        pageInfo = loadPageInfo()
    }

    LaunchedEffect(pageIndex, showResearchDebugOverlay) {
        layoutDebugInfo = if (showResearchDebugOverlay) loadPageLayoutDebugInfo() else null
    }

    LaunchedEffect(pageIndex, containerWidth) {
        if (containerWidth > 1) {
            bitmap = loadBitmap(containerWidth)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = "Page ${pageIndex + 1}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                .background(Color.White)
                .pointerInput(pageInfo) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            val currentPageInfo = pageInfo ?: return@detectTapGestures
                            val normalizedX = tapOffset.x / size.width.toFloat()
                            val normalizedY = tapOffset.y / size.height.toFloat()
                            if (normalizedX in 0f..1f && normalizedY in 0f..1f) {
                                onWordDoubleTapped(pageIndex, normalizedX, normalizedY)
                            }
                        }
                    )
                }
                .onSizeChanged {
                    containerWidth = it.width
                    onSizeChanged(it)
                }
        ) {
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Document page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
                val highlightWord = currentWord?.takeIf { it.pageIndex == pageIndex }
                if (highlightWord != null && pageInfo != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val rect = highlightWord.boundingBox
                        val left = (rect.left / pageInfo!!.widthPoints.toFloat()) * size.width
                        val top = (rect.top / pageInfo!!.heightPoints.toFloat()) * size.height
                        val right = (rect.right / pageInfo!!.widthPoints.toFloat()) * size.width
                        val bottom = (rect.bottom / pageInfo!!.heightPoints.toFloat()) * size.height
                        drawRect(
                            color = if (currentMode == HighlightMode.WORD) Color(0x66FFD54F) else Color(0x44A5D6A7),
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top)
                        )
                    }
                }
                if (showResearchDebugOverlay && layoutDebugInfo != null) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawResearchDebugOverlay(layoutDebugInfo!!)
                    }
                }
                if (showResearchDebugOverlay) {
                    ResearchDebugBadge(
                        layoutDebugInfo = layoutDebugInfo,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ResearchDebugBadge(
    layoutDebugInfo: PdfPageLayoutDebugInfo?,
    modifier: Modifier = Modifier
) {
    val (label, color) = when (layoutDebugInfo?.mode) {
        PdfPageReadingMode.SINGLE_COLUMN -> "Research Debug: Single" to Color(0xFFD32F2F)
        PdfPageReadingMode.TWO_COLUMNS -> "Research Debug: Two Columns" to Color(0xFF00838F)
        PdfPageReadingMode.HEADER_THEN_TWO_COLUMNS -> "Research Debug: Header + Columns" to Color(0xFFEF6C00)
        null -> "Research Debug: Analyzing" to Color(0xFF5D4037)
    }
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.88f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawResearchDebugOverlay(
    info: PdfPageLayoutDebugInfo
) {
    val splitRatio = info.splitRatio
    if (splitRatio != null) {
        val splitX = size.width * splitRatio
        drawLine(
            color = Color(0xFF00BCD4),
            start = Offset(splitX, 0f),
            end = Offset(splitX, size.height),
            strokeWidth = 5.dp.toPx()
        )
        drawRect(
            color = Color(0x2A00BCD4),
            topLeft = Offset(splitX - 18.dp.toPx(), 0f),
            size = Size(36.dp.toPx(), size.height)
        )
    }

    val columnTopRatio = info.columnTopRatio
    if (info.mode == PdfPageReadingMode.HEADER_THEN_TWO_COLUMNS && columnTopRatio != null) {
        val columnTop = size.height * columnTopRatio
        drawLine(
            color = Color(0xFFFF6D00),
            start = Offset(0f, columnTop),
            end = Offset(size.width, columnTop),
            strokeWidth = 5.dp.toPx()
        )
        drawRect(
            color = Color(0x24FF6D00),
            topLeft = Offset(0f, 0f),
            size = Size(size.width, columnTop)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderControls(
    state: ReaderUiState,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPreviousSentence: () -> Unit,
    onNextSentence: () -> Unit,
    onPreviousParagraph: () -> Unit,
    onNextParagraph: () -> Unit,
    onRateChange: (Float) -> Unit,
    onVoiceChange: (String) -> Unit,
    onCloudTtsChanged: (Boolean) -> Unit,
    onResearchPaperModeChanged: (Boolean) -> Unit,
    onStoryModeChanged: (Boolean) -> Unit,
    onSummarize: () -> Unit,
    onPlaySummary: () -> Unit,
    onStopSummary: () -> Unit,
    onHideControls: () -> Unit
) {
    var voiceMenuExpanded by remember { mutableStateOf(false) }
    val playing = state.playbackState is PlaybackState.Playing
    val preparing = state.playbackState is PlaybackState.Preparing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        LinearProgressIndicator(
            progress = {
                if (state.currentHighlight == null || state.totalWordCount == 0) {
                    0f
                } else {
                    state.currentHighlight.word.globalWordIndex.toFloat() / state.totalWordCount.toFloat()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousParagraph) {
                Icon(Icons.Outlined.NavigateBefore, contentDescription = "Previous paragraph")
            }
            IconButton(onClick = onPreviousSentence) {
                Icon(Icons.Outlined.NavigateBefore, contentDescription = "Previous sentence")
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(56.dp)) {
                if (preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play"
                    )
                }
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = "Stop")
            }
            IconButton(onClick = onNextSentence) {
                Icon(Icons.Outlined.NavigateNext, contentDescription = "Next sentence")
            }
            IconButton(onClick = onNextParagraph) {
                Icon(Icons.Outlined.NavigateNext, contentDescription = "Next paragraph")
            }
        }
        Spacer(Modifier.height(12.dp))
        if (preparing) {
            Text(
                text = if (state.settings.useCloudTts) {
                    "Connecting to cloud voice..."
                } else {
                    "Preparing speech..."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
        }
        Text(
            text = "Speed ${"%.2f".format(state.settings.speechRate)}x",
            style = MaterialTheme.typography.labelMedium
        )
        Slider(
            value = state.settings.speechRate,
            onValueChange = onRateChange,
            valueRange = 0.5f..2.0f
        )
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Cloud Voice",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Streams paragraph chunks from Rumik with a 150-word request limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.settings.useCloudTts,
                onCheckedChange = onCloudTtsChanged
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Research Paper Mode",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Uses on-device OCR to detect wide two-column layouts and reads the left section before the right.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.settings.researchPaperMode,
                onCheckedChange = onResearchPaperModeChanged
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Story Mode",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Adds a 10-second Freesound preview under cloud narration when the text matches a sound cue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = state.settings.storyMode,
                onCheckedChange = onStoryModeChanged
            )
        }
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        if (state.settings.useCloudTts) {
            Text(
                text = "Rumik Cloud - Muga",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "${state.settings.cloudVoice.gender.replaceFirstChar { it.uppercase() }} voice, ${state.settings.cloudVoice.age}, ${state.settings.cloudVoice.region.displayName} accent, casual conversational pace.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ExposedDropdownMenuBox(
                expanded = voiceMenuExpanded,
                onExpandedChange = { voiceMenuExpanded = !voiceMenuExpanded }
            ) {
                TextField(
                    value = state.voices.firstOrNull { it.voiceName == state.settings.voiceName }?.displayName ?: "System voice",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Voice") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voiceMenuExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = voiceMenuExpanded,
                    onDismissRequest = { voiceMenuExpanded = false }
                ) {
                    state.voices.forEach { voice ->
                        DropdownMenuItem(
                            text = { Text(voice.displayName) },
                            onClick = {
                                voiceMenuExpanded = false
                                onVoiceChange(voice.voiceName)
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ExecutiveBriefPanel(
            state = state,
            onSummarize = onSummarize,
            onPlaySummary = onPlaySummary,
            onStopSummary = onStopSummary
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onHideControls) {
                Text("Hide Below")
            }
        }
    }
}

@Composable
private fun ExecutiveBriefPanel(
    state: ReaderUiState,
    onSummarize: () -> Unit,
    onPlaySummary: () -> Unit,
    onStopSummary: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        tonalElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Executive Brief",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "OpenRouter ${"openai/gpt-oss-20b:free"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onSummarize,
                    enabled = !state.isSummaryLoading && state.documentInfo?.hasExtractableText == true
                ) {
                    Text(if (state.summaryText == null) "Summarize" else "Refresh")
                }
            }
            if (state.isSummaryLoading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.summaryError?.let { message ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.summaryText?.let { summary ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlaySummary,
                        enabled = !state.isSummaryLoading && !state.isSummaryPlaybackActive
                    ) {
                        Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = onStopSummary,
                        enabled = state.isSummaryPlaybackActive
                    ) {
                        Icon(Icons.Outlined.Stop, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Stop")
                    }
                }
            }
        }
    }
}

private fun ReaderError.toMessage(): String {
    return when (this) {
        ReaderError.CorruptedPdf -> "This PDF appears to be corrupted and could not be opened."
        ReaderError.DocumentNotFound -> "The selected document is no longer available."
        ReaderError.EncryptedPdf -> "Encrypted PDFs are not supported without the password."
        ReaderError.ImageOnlyPdf -> "This looks like a scanned PDF without extractable text."
        ReaderError.MissingTtsEngine -> "No on-device Text-to-Speech engine is available."
        ReaderError.StoragePermissionDenied -> "Read permission for the selected document was denied."
        ReaderError.UnsupportedDocumentFormat -> "This document format is recognized but cannot be rendered on this device yet."
        ReaderError.UnsupportedLanguage -> "The selected TTS language or voice is not supported on this device."
        is ReaderError.Unknown -> this.message
    }
}
