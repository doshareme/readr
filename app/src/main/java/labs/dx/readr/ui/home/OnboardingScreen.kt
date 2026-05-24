package labs.dx.readr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.KeyboardDoubleArrowUp
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onPrimaryAction: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                title = "Read papers like a calm narrator is beside you.",
                body = "Pick a PDF from storage, keep speech fully on-device, and let the app remember exactly where you stopped.",
                eyebrow = "WELCOME TO READR",
                accent = listOf(Color(0xFFF4E8C9), Color(0xFFEBC5A3)),
                icon = Icons.Outlined.AutoStories,
                bullets = listOf(
                    "On-device TTS keeps narration private",
                    "Resume from your saved reading position",
                    "Built for long-form technical documents"
                )
            ),
            OnboardingPage(
                title = "Double-tap anywhere on the PDF to begin from that spot.",
                body = "When you find the paragraph you care about, just double-tap in the document and Readr starts speaking from the nearest word instead of making you scrub for it.",
                eyebrow = "CONTROL THE FLOW",
                accent = listOf(Color(0xFFD9F0E5), Color(0xFFAED1C4)),
                icon = Icons.Outlined.TouchApp,
                bullets = listOf(
                    "Jump straight into the section you want",
                    "Perfect for revisiting methods, tables, and citations",
                    "The highlight locks onto the nearest spoken word"
                )
            ),
            OnboardingPage(
                title = "Swipe up anywhere to open voice and playback controls.",
                body = "The reader keeps controls tucked away until you need them. A quick upward swipe reveals speed, voice options, and playback controls without leaving the page.",
                eyebrow = "VOICE AT YOUR FINGERTIPS",
                accent = listOf(Color(0xFFF3E1FF), Color(0xFFD5C5F5)),
                icon = Icons.Outlined.KeyboardDoubleArrowUp,
                bullets = listOf(
                    "Adjust playback speed while the PDF stays visible",
                    "Customize voice and language on the fly",
                    "Hide the sheet again when you want a cleaner page view"
                ),
                companionIcon = Icons.Outlined.RecordVoiceOver
            ),
            OnboardingPage(
                title = "Playback is designed for scanning, revisiting, and focusing.",
                body = "Jump sentence by sentence, move paragraph by paragraph, and keep the spoken word visible without the reader fighting your scroll.",
                eyebrow = "STAY IN CONTROL",
                accent = listOf(Color(0xFFDDEBFF), Color(0xFFBED3F2)),
                icon = Icons.Outlined.GraphicEq,
                bullets = listOf(
                    "Tap play from the beginning or saved progress",
                    "Use sentence and paragraph jumps for fast review",
                    "The highlight follows speech while you stay in control"
                )
            ),
            OnboardingPage(
                title = "Research mode helps when papers stop behaving like plain text.",
                body = "For dense two-column papers, you can switch on research mode and let the reader work through the layout more naturally before you dive into the library.",
                eyebrow = "FOR RESEARCH PDFs",
                accent = listOf(Color(0xFFF4E7D5), Color(0xFFE4C9AE)),
                icon = Icons.Outlined.AutoStories,
                bullets = listOf(
                    "Library history keeps recent papers close",
                    "Suggested titles surface what you revisit often",
                    "You can pin important papers to the front"
                )
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) {
                    Text("Skip")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                OnboardingPageCard(page = pages[pageIndex])
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.forEachIndexed { index, _ ->
                        val selected = index == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (selected) 28.dp else 10.dp, 10.dp)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                    shape = CircleShape
                                )
                        )
                    }
                }

                Button(
                    onClick = {
                        if (lastPage) {
                            onPrimaryAction()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Text(if (lastPage) "Start with a PDF" else "Continue")
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageCard(page: OnboardingPage) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = page.eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        brush = Brush.linearGradient(page.accent),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                        page.companionIcon?.let { companion ->
                            Surface(
                                color = Color.White.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Icon(
                                    imageVector = companion,
                                    contentDescription = null,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        page.bullets.forEach { bullet ->
                            Surface(
                                color = Color.White.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class OnboardingPage(
    val eyebrow: String,
    val title: String,
    val body: String,
    val accent: List<Color>,
    val icon: ImageVector,
    val bullets: List<String>,
    val companionIcon: ImageVector? = null
)
