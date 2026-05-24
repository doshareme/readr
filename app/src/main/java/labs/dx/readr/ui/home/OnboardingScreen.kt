package labs.dx.readr.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import labs.dx.core.domain.model.CloudVoicePreferences
import labs.dx.core.domain.model.CloudVoiceRegion
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun OnboardingScreen(
    onSkip: (CloudVoicePreferences) -> Unit,
    onPrimaryAction: (CloudVoicePreferences) -> Unit
) {
    var selectedGender by remember { mutableStateOf("male") }
    var selectedAge by remember { mutableStateOf("30s") }
    var selectedRegion by remember { mutableStateOf(CloudVoiceRegion.African) }
    val voicePreferences = CloudVoicePreferences(
        gender = selectedGender,
        age = selectedAge,
        region = selectedRegion
    )
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
    val onboardingStepCount = pages.size + VoiceSetupPageCount
    val pagerState = rememberPagerState(pageCount = { onboardingStepCount })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == onboardingStepCount - 1

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
                TextButton(onClick = { onSkip(voicePreferences) }) {
                    Text("Skip")
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { pageIndex ->
                when (pageIndex) {
                    0 -> VoiceProfilePage(
                        selectedGender = selectedGender,
                        onGenderSelected = { selectedGender = it },
                        selectedAge = selectedAge,
                        onAgeSelected = { selectedAge = it }
                    )
                    1 -> VoiceRegionPage(
                        selectedRegion = selectedRegion,
                        onRegionSelected = { selectedRegion = it }
                    )
                    else -> OnboardingPageCard(page = pages[pageIndex - VoiceSetupPageCount])
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(onboardingStepCount) { index ->
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
                            onPrimaryAction(voicePreferences)
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
private fun VoiceProfilePage(
    selectedGender: String,
    onGenderSelected: (String) -> Unit,
    selectedAge: String,
    onAgeSelected: (String) -> Unit
) {
    VoiceSetupCard(
        eyebrow = "VOICE PROFILE",
        title = "Choose the voice that should read with you.",
        body = "This tunes the cloud narration prompt before your first PDF opens."
    ) {
        OptionRow(
            label = "Voice",
            options = listOf("male" to "Male", "female" to "Female"),
            selectedValue = selectedGender,
            onSelected = onGenderSelected
        )
        Spacer(Modifier.height(18.dp))
        OptionRow(
            label = "Age",
            options = listOf("20s" to "20s", "30s" to "30s", "40s" to "40s"),
            selectedValue = selectedAge,
            onSelected = onAgeSelected
        )
    }
}

@Composable
private fun VoiceRegionPage(
    selectedRegion: CloudVoiceRegion,
    onRegionSelected: (CloudVoiceRegion) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    VoiceSetupCard(
        eyebrow = "VOICE AREA",
        title = "Choose your area.",
        body = "Tap a continent on the map or choose from the selector."
    ) {
        WorldRegionMap(
            selectedRegion = selectedRegion,
            onRegionSelected = onRegionSelected
        )
        Spacer(Modifier.height(16.dp))
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = selectedRegion.displayName,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Text("Select")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                CloudVoiceRegion.entries.forEach { region ->
                    DropdownMenuItem(
                        text = { Text(region.displayName) },
                        onClick = {
                            onRegionSelected(region)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceSetupCard(
    eyebrow: String,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
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
                    text = eyebrow,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    options: List<Pair<String, String>>,
    selectedValue: String,
    onSelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            options.forEach { (value, labelText) ->
                val selected = value == selectedValue
                val colors = if (selected) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
                OutlinedButton(
                    onClick = { onSelected(value) },
                    modifier = Modifier.weight(1f),
                    colors = colors,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp)
                ) {
                    Text(labelText)
                }
            }
        }
    }
}

@Composable
private fun WorldRegionMap(
    selectedRegion: CloudVoiceRegion,
    onRegionSelected: (CloudVoiceRegion) -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val selectedColor = MaterialTheme.colorScheme.tertiary
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(24.dp)
                )
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        regionAt(offset, size.width.toFloat(), size.height.toFloat())
                            ?.let(onRegionSelected)
                    }
                }
                .padding(10.dp)
        ) {
            drawWorldRegion(CloudVoiceRegion.American, selectedRegion, primary, selectedColor, surfaceVariant)
            drawWorldRegion(CloudVoiceRegion.European, selectedRegion, primary, selectedColor, surfaceVariant)
            drawWorldRegion(CloudVoiceRegion.African, selectedRegion, primary, selectedColor, surfaceVariant)
            drawWorldRegion(CloudVoiceRegion.Asian, selectedRegion, primary, selectedColor, surfaceVariant)
            drawWorldRegion(CloudVoiceRegion.Indian, selectedRegion, primary, selectedColor, surfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CloudVoiceRegion.entries.forEach { region ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onRegionSelected(region) },
                    color = if (region == selectedRegion) selectedColor.copy(alpha = 0.18f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = region.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (region == selectedRegion) onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawWorldRegion(
    region: CloudVoiceRegion,
    selectedRegion: CloudVoiceRegion,
    primary: Color,
    selectedColor: Color,
    surfaceVariant: Color
) {
    val selected = region == selectedRegion
    val color = when {
        selected -> selectedColor
        else -> primary.copy(alpha = 0.42f)
    }
    val path = regionPath(region, size.width, size.height)
    drawPath(
        path = path,
        color = if (selected) color else surfaceVariant.copy(alpha = 0.95f)
    )
    drawPath(
        path = path,
        color = color,
        alpha = if (selected) 0.95f else 0.72f
    )
}

private fun regionAt(offset: Offset, width: Float, height: Float): CloudVoiceRegion? {
    val hitOrder = listOf(
        CloudVoiceRegion.Indian,
        CloudVoiceRegion.American,
        CloudVoiceRegion.European,
        CloudVoiceRegion.African,
        CloudVoiceRegion.Asian
    )
    return hitOrder.firstOrNull { region ->
        regionBounds(region, width, height).contains(offset)
    }
}

private fun regionPath(region: CloudVoiceRegion, width: Float, height: Float): Path {
    val rect = regionBounds(region, width, height)
    return Path().apply {
        moveTo(rect.left + rect.width * 0.16f, rect.top + rect.height * 0.34f)
        cubicTo(rect.left, rect.top + rect.height * 0.18f, rect.left + rect.width * 0.22f, rect.top, rect.left + rect.width * 0.48f, rect.top + rect.height * 0.08f)
        cubicTo(rect.right - rect.width * 0.08f, rect.top + rect.height * 0.14f, rect.right, rect.top + rect.height * 0.34f, rect.right - rect.width * 0.16f, rect.top + rect.height * 0.52f)
        cubicTo(rect.right - rect.width * 0.26f, rect.bottom - rect.height * 0.08f, rect.left + rect.width * 0.52f, rect.bottom, rect.left + rect.width * 0.34f, rect.bottom - rect.height * 0.16f)
        cubicTo(rect.left + rect.width * 0.1f, rect.bottom - rect.height * 0.32f, rect.left + rect.width * 0.02f, rect.top + rect.height * 0.54f, rect.left + rect.width * 0.16f, rect.top + rect.height * 0.34f)
        close()
    }
}

private fun regionBounds(region: CloudVoiceRegion, width: Float, height: Float): Rect {
    fun rect(left: Float, top: Float, right: Float, bottom: Float) = Rect(
        left * width,
        top * height,
        right * width,
        bottom * height
    )
    return when (region) {
        CloudVoiceRegion.American -> rect(0.06f, 0.2f, 0.31f, 0.78f)
        CloudVoiceRegion.European -> rect(0.43f, 0.14f, 0.57f, 0.42f)
        CloudVoiceRegion.African -> rect(0.44f, 0.4f, 0.62f, 0.84f)
        CloudVoiceRegion.Asian -> rect(0.58f, 0.2f, 0.91f, 0.7f)
        CloudVoiceRegion.Indian -> rect(0.64f, 0.52f, 0.76f, 0.86f)
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

private const val VoiceSetupPageCount = 2
