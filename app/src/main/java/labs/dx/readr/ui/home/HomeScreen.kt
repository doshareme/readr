package labs.dx.readr.ui.home

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import labs.dx.core.domain.model.DocumentFormat
import labs.dx.core.domain.model.PdfHistoryEntry

@Composable
fun HomeScreen(
    onOpenPdf: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.toString()?.let { uriString ->
            viewModel.onDocumentSelected(uriString)
            onOpenPdf(uriString)
        }
    }

    fun launchPicker() {
        launcher.launch(DocumentFormat.pickerMimeTypes)
    }

    if (state.showOnboarding) {
        OnboardingScreen(
            onSkip = viewModel::completeOnboarding,
            onPrimaryAction = { voicePreferences ->
                viewModel.completeOnboarding(voicePreferences)
                launchPicker()
            }
        )
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = null
                    )
                    Text(
                        text = "Narrate documents with on-device speech, live word highlighting, and resumable progress.",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                    Text(
                        text = "Choose PDF, EPUB, Word, text, HTML, XML, or comic archives from storage.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
                    )
                    Button(
                        onClick = ::launchPicker,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                    ) {
                        Text("Select Document")
                    }
                }
            }

            if (state.suggested.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Suggested Titles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.suggested, key = { it.document.documentId }) { entry ->
                                SuggestedPdfCard(
                                    entry = entry,
                                    onClick = {
                                        viewModel.onHistoryOpened(entry)
                                        onOpenPdf(entry.document.uriString)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (state.history.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "Your recent documents will appear here. Pin the ones you revisit often and remove anything you no longer need.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(state.history, key = { it.document.documentId }) { entry ->
                    HistoryPdfRow(
                        entry = entry,
                        onOpen = {
                            viewModel.onHistoryOpened(entry)
                            onOpenPdf(entry.document.uriString)
                        },
                        onRemove = { viewModel.onRemoveHistory(entry.document.documentId) },
                        onTogglePin = {
                            viewModel.onPinnedChanged(entry.document.documentId, !entry.isPinned)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedPdfCard(
    entry: PdfHistoryEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CoverThumbnail(
                coverImagePath = entry.coverImagePath,
                displayName = entry.document.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
            )
            Text(
                text = entry.document.displayName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.document.uriString,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = rememberOpenedLabel(entry.lastOpenedAtEpochMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun HistoryPdfRow(
    entry: PdfHistoryEntry,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onTogglePin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .height(78.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                CoverThumbnail(
                    coverImagePath = entry.coverImagePath,
                    displayName = entry.document.displayName,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = entry.document.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.isPinned) {
                        Text(
                            text = "Pinned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = entry.document.uriString,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = rememberOpenedLabel(entry.lastOpenedAtEpochMillis),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = if (entry.isPinned) "Unpin document" else "Pin document",
                    tint = if (entry.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.Close, contentDescription = "Remove document from history")
            }
        }
    }
}

@Composable
private fun CoverThumbnail(
    coverImagePath: String?,
    displayName: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(coverImagePath) {
        coverImagePath?.let { path -> BitmapFactory.decodeFile(path) }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "$displayName cover",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Outlined.AutoStories, contentDescription = null)
        }
    }
}

@Composable
private fun rememberOpenedLabel(timestamp: Long): String {
    val elapsedMillis = (System.currentTimeMillis() - timestamp).coerceAtLeast(0L)
    val elapsedMinutes = elapsedMillis / 60_000L
    val label = when {
        elapsedMinutes < 60L -> "${elapsedMinutes} m ago"
        elapsedMinutes < 1_440L -> "${elapsedMinutes / 60L} h ago"
        else -> "${elapsedMinutes / 1_440L} d ago"
    }
    return "Last Read $label"
}
