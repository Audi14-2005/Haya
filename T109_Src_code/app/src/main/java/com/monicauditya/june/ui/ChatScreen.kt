package com.monicauditya.june.ui

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monicauditya.june.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

private val ChatObsidian = Color(0xFF131317)
private val ChatPrimary = Color(0xFFCEBDFF)
private val ChatPrimaryContainer = Color(0xFF9B7FED)
private val ChatSurfaceLowest = Color(0xFF0E0E12)
private val ChatSurfaceLow = Color(0xFF1B1B1F)
private val ChatSurfaceHigh = Color(0xFF353439)
private val ChatOnSurface = Color(0xFFE4E1E7)
private val ChatOnSurfaceVariant = Color(0xFFCBC3D7)
private val ChatOutlineVariant = Color(0xFF2A292E)
private val ChatDanger = Color(0xFFFF7BA4)
private val ChatWarning = Color(0xFFFFC973)
private val ChatSuccess = Color(0xFF8CE3B1)

private val ChatDisplayFamily = FontFamily(
    Font(R.font.sf_pro_text_black, FontWeight.Black),
    Font(R.font.sf_pro_text_bold, FontWeight.Bold),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
)

private val ChatBodyFamily = FontFamily(
    Font(R.font.sf_pro_text_regular, FontWeight.Normal),
    Font(R.font.sf_pro_text_medium, FontWeight.Medium),
    Font(R.font.sf_pro_text_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_text_bold, FontWeight.Bold),
)

private val ChatLabelFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val ChatEditorialColors = darkColorScheme(
    background = ChatObsidian,
    surface = ChatSurfaceLow,
    surfaceVariant = ChatSurfaceHigh,
    primary = ChatPrimary,
    primaryContainer = ChatPrimaryContainer,
    onPrimary = ChatObsidian,
    onPrimaryContainer = Color(0xFFFAF7FF),
    onBackground = ChatOnSurface,
    onSurface = ChatOnSurface,
    onSurfaceVariant = ChatOnSurfaceVariant,
    outline = ChatOutlineVariant,
    error = ChatDanger,
    onError = ChatObsidian,
    errorContainer = Color(0xFF31131D),
    onErrorContainer = Color(0xFFFFD9E2),
)

private val ChatEditorialTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = ChatDisplayFamily,
        fontWeight = FontWeight.Black,
        fontSize = 42.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = ChatDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = ChatDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(fontFamily = ChatBodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = ChatBodyFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = ChatBodyFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = ChatBodyFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodySmall = TextStyle(fontFamily = ChatBodyFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = ChatLabelFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = ChatLabelFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 1.4.sp),
)

private enum class ComposerAction {
    Send,
    Stop,
}

data class ChatModelOption(
    val modelId: String,
    val title: String,
    val modelPath: String,
)

private fun formatChatModelTitle(raw: String): String {
    val normalized = raw
        .replace(Regex("""_[0-9a-f]{6,}$""", RegexOption.IGNORE_CASE), "")
        .replace('_', ' ')
        .replace('-', ' ')
        .replace(Regex("""\((q[0-9][a-z0-9_\-]*)\)""", RegexOption.IGNORE_CASE), " $1 ")
        .replace(Regex("""\s+"""), " ")
        .trim()

    val lower = normalized.lowercase()
    val sizeToken = Regex("""\b\d+(?:\.\d+)?b\b""", RegexOption.IGNORE_CASE)
        .find(lower)
        ?.value
        ?.uppercase()
    val quantToken = Regex("""\bq\d+\b""", RegexOption.IGNORE_CASE)
        .find(lower)
        ?.value
        ?.uppercase()

    val ignored = setOf(
        "gguf", "chat", "instruct", "instruction", "it", "preview", "base",
        "km", "ks", "m", "s", "k", "model"
    )

    val nameTokens = normalized.split(" ")
        .filter { it.isNotBlank() }
        .filterNot { token ->
            val lowerToken = token.lowercase().trim('(', ')', '[', ']', '{', '}', ',', '.')
            lowerToken in ignored ||
                Regex("""\d+(?:\.\d+)?b""", RegexOption.IGNORE_CASE).matches(lowerToken) ||
                Regex("""q\d+.*""", RegexOption.IGNORE_CASE).matches(lowerToken)
        }
        .fold(mutableListOf<String>()) { acc, token ->
            val cleanedToken = token.trim('(', ')', '[', ']', '{', '}', ',', '.')
            if (cleanedToken.isNotBlank() && acc.none { it.equals(cleanedToken, ignoreCase = true) }) {
                acc += cleanedToken.lowercase().replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                }
            }
            acc
        }

    val compactBase = buildString {
        append(nameTokens.take(3).joinToString(" ").ifBlank { "Model" })
        if (!sizeToken.isNullOrBlank()) append(" $sizeToken")
        if (!quantToken.isNullOrBlank()) append(" $quantToken")
    }.replace(Regex("""\s{2,}"""), " ").trim()

    val finalTokens = compactBase
        .split(" ")
        .filter { it.isNotBlank() }
        .fold(mutableListOf<String>()) { acc, token ->
            val normalizedToken = token.lowercase()
            if (acc.none { it.lowercase() == normalizedToken }) {
                acc += token
            }
            acc
        }

    return finalTokens.joinToString(" ").trim()
}

@Composable
fun ChatScreen(
    state: ChatState,
    onSendMessage: (String) -> Unit,
    onImportModel: (android.net.Uri) -> Unit = {},
    onStopGeneration: () -> Unit = {},
    modelOptions: List<ChatModelOption> = emptyList(),
    activeModelId: String? = null,
    modelSwitchInProgress: Boolean = false,
    onMenuClick: () -> Unit = {},
    onSelectModel: (String) -> Unit = {},
) {
    MaterialTheme(
        colorScheme = ChatEditorialColors,
        typography = ChatEditorialTypography,
    ) {
        var inputText by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        val modelReady = state.modelState == ModelState.Ready
        val interactionLocked = state.modelState == ModelState.Loading || state.isGenerating || modelSwitchInProgress
        val composerOverlayHeight = 84.dp
        var isAutoFollowEnabled by remember { mutableStateOf(true) }
        var programmaticScrollInProgress by remember { mutableStateOf(false) }
        val streamingText by remember(state.currentStreamingMessage) { derivedStateOf { state.currentStreamingMessage } }
        val streamingBucket = remember(streamingText) { streamingText.length / 48 }
        val isAtBottom by remember(listState) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
                val isLastItem = lastVisibleItem.index == layoutInfo.totalItemsCount - 1
                val viewportEnd = layoutInfo.viewportEndOffset
                val itemEnd = lastVisibleItem.offset + lastVisibleItem.size
                isLastItem && itemEnd <= viewportEnd + 20
            }
        }
        val latestState by rememberUpdatedState(state)
        val latestAutoFollowEnabled by rememberUpdatedState(isAutoFollowEnabled)
        val latestProgrammaticScroll by rememberUpdatedState(programmaticScrollInProgress)
        val jumpButtonAlpha by animateFloatAsState(
            targetValue = if (!isAutoFollowEnabled) 1f else 0f,
            animationSpec = tween(durationMillis = 180, delayMillis = if (!isAutoFollowEnabled) 50 else 0),
            label = "jump_button_alpha"
        )
        val jumpButtonScale by animateFloatAsState(
            targetValue = if (!isAutoFollowEnabled) 1f else 0.92f,
            animationSpec = tween(durationMillis = 220, delayMillis = if (!isAutoFollowEnabled) 50 else 0),
            label = "jump_button_scale"
        )

        suspend fun animateToLatest() {
            val latest = latestState
            val targetIndex = when {
                latest.isGenerating -> latest.messages.size
                latest.messages.isNotEmpty() -> latest.messages.lastIndex
                else -> return
            }
            programmaticScrollInProgress = true
            try {
                listState.animateScrollToItem(targetIndex, scrollOffset = Int.MAX_VALUE)
            } finally {
                programmaticScrollInProgress = false
            }
        }

        suspend fun snapToLatest() {
            val latest = latestState
            val targetIndex = when {
                latest.isGenerating -> latest.messages.size
                latest.messages.isNotEmpty() -> latest.messages.lastIndex
                else -> return
            }
            programmaticScrollInProgress = true
            try {
                listState.scrollToItem(targetIndex, scrollOffset = Int.MAX_VALUE)
            } finally {
                programmaticScrollInProgress = false
            }
        }

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }.collect { isScrolling ->
                if (isScrolling && !latestProgrammaticScroll) isAutoFollowEnabled = false
            }
        }

        LaunchedEffect(isAtBottom, listState.isScrollInProgress) {
            if (isAtBottom && !listState.isScrollInProgress) isAutoFollowEnabled = true
        }

        LaunchedEffect(state.messages.size, state.isGenerating, state.isThinking, streamingBucket) {
            if (!latestAutoFollowEnabled || listState.isScrollInProgress) return@LaunchedEffect
            delay(if (state.isGenerating) 90 else 40)
            if (!latestAutoFollowEnabled || listState.isScrollInProgress) return@LaunchedEffect
            snapToLatest()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .background(ChatObsidian)
        ) {
            ChatBackdrop()

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ChatSurfaceLow.copy(alpha = 0.72f),
                                ChatSurfaceLow.copy(alpha = 0.42f),
                                ChatObsidian.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            ) {
                ChatHeader(
                    modelOptions = modelOptions,
                    activeModelId = activeModelId,
                    isSwitching = modelSwitchInProgress,
                    onMenuClick = onMenuClick,
                    enabled = !interactionLocked,
                    onSelectModel = onSelectModel
                )

                if (state.modelState == ModelState.Error || state.errorMessage != null) {
                    EditorialStatusStrip(state = state)
                }

                if (state.messages.isEmpty() && !state.isGenerating) {
                    EmptyPromptCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = composerOverlayHeight + 16.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            contentPadding = PaddingValues(bottom = composerOverlayHeight + 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            itemsIndexed(
                                items = state.messages,
                                key = { _, message -> message.id }
                            ) { index, message ->
                                val staggerIndex = max(0, index - (state.messages.lastIndex - 2).coerceAtLeast(0))
                                MessageBubble(message = message, entryDelayMs = staggerIndex * 35L)
                            }

                            if (state.isGenerating) {
                                item(key = "assistant_stream") {
                                    StreamingMessageBubble(
                                        text = streamingText,
                                        isThinking = state.isThinking
                                    )
                                }
                            }
                        }

                        if (!isAutoFollowEnabled || jumpButtonAlpha > 0.01f) {
                            GlassIsland(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 20.dp, bottom = composerOverlayHeight + 4.dp)
                                    .graphicsLayer {
                                        alpha = jumpButtonAlpha
                                        scaleX = jumpButtonScale
                                        scaleY = jumpButtonScale
                                    },
                                shape = RoundedCornerShape(18.dp),
                                tonalColor = ChatSurfaceHigh.copy(alpha = 0.88f),
                            ) {
                                MotionIconButton(
                                    onClick = {
                                        isAutoFollowEnabled = true
                                        coroutineScope.launch { animateToLatest() }
                                    }
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Jump to latest", tint = ChatPrimary)
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(composerOverlayHeight + 14.dp)
                        .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ChatSurfaceLow.copy(alpha = 0.18f),
                                    ChatSurfaceLow.copy(alpha = 0.68f),
                                    ChatSurfaceLow.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .drawWithCache {
                            onDrawBehind {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.08f),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                )
                ComposerDock(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modelReady = modelReady,
                    interactionLocked = interactionLocked,
                    action = if (state.isGenerating) ComposerAction.Stop else ComposerAction.Send,
                    onPrimaryAction = {
                        if (state.isGenerating) {
                            onStopGeneration()
                        } else if (inputText.isNotBlank() && modelReady) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyPromptCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Whats on your mind?",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChatBackdrop() {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            ChatPrimary.copy(alpha = 0.16f),
                            ChatPrimaryContainer.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ChatSurfaceLowest.copy(alpha = 0.92f),
                            ChatObsidian.copy(alpha = 0.72f),
                            ChatObsidian
                        )
                    )
                )
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val spacing = 24.dp.toPx()
            val radius = 1.2.dp.toPx()
            val dotColor = Color.White.copy(alpha = 0.055f)
            var x = spacing / 2f
            while (x < size.width) {
                var y = spacing / 2f
                while (y < size.height) {
                    drawCircle(dotColor, radius, center = androidx.compose.ui.geometry.Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
    }
}

@Composable
private fun ChatHeader(
    modelOptions: List<ChatModelOption>,
    activeModelId: String?,
    isSwitching: Boolean,
    onMenuClick: () -> Unit,
    enabled: Boolean,
    onSelectModel: (String) -> Unit,
) {
    val currentOption = remember(modelOptions, activeModelId) {
        modelOptions.firstOrNull { it.modelId == activeModelId } ?: modelOptions.firstOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        GlassIsland(
            modifier = Modifier.align(Alignment.CenterStart),
            shape = RoundedCornerShape(18.dp),
            tonalColor = ChatSurfaceLow.copy(alpha = 0.82f)
        ) {
            MotionIconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        currentOption?.let { option ->
            ModelSwitcherIsland(
                modifier = Modifier.align(Alignment.CenterEnd),
                currentOption = option,
                options = modelOptions,
                isSwitching = isSwitching,
                enabled = enabled,
                activeModelId = activeModelId,
                onSelectModel = onSelectModel
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModelSwitcherIsland(
    modifier: Modifier = Modifier,
    currentOption: ChatModelOption,
    options: List<ChatModelOption>,
    isSwitching: Boolean,
    enabled: Boolean,
    activeModelId: String?,
    onSelectModel: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hasMultiple = options.size > 1

    Box(modifier = modifier) {
        GlassIsland(
            modifier = Modifier
                .graphicsLayer { alpha = if (enabled) 1f else 0.72f }
                .combinedClickable(
                    enabled = enabled && hasMultiple,
                    onClick = { expanded = true }
                ),
            shape = RoundedCornerShape(18.dp),
            tonalColor = ChatSurfaceLow.copy(alpha = 0.88f)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .widthIn(max = 196.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isSwitching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.8.dp,
                        color = ChatPrimary
                    )
                }
                Text(
                    text = formatChatModelTitle(currentOption.title),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (hasMultiple) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch model",
                        tint = ChatPrimary
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded && hasMultiple,
            onDismissRequest = { expanded = false },
            containerColor = ChatSurfaceLow,
            modifier = Modifier.background(Color.Transparent)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatChatModelTitle(option.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (option.modelId == activeModelId) ChatPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelectModel(option.modelId)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorialStatusStrip(state: ChatState) {
    val isError = state.modelState == ModelState.Error || state.errorMessage != null
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        tonalColor = if (isError) Color(0xFF2A1620).copy(alpha = 0.88f) else ChatSurfaceLow.copy(alpha = 0.84f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!isError) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = ChatPrimary
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(ChatDanger, CircleShape)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isError) "RUNTIME NOTICE" else "RUNTIME WARMING",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) ChatDanger else ChatPrimary
                )
                Text(
                    text = state.errorMessage ?: state.modelStatusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ComposerDock(
    value: String,
    onValueChange: (String) -> Unit,
    modelReady: Boolean,
    interactionLocked: Boolean,
    action: ComposerAction,
    onPrimaryAction: () -> Unit,
) {
    val density = LocalDensity.current
    val dockBottomPadding = with(density) {
        (WindowInsets.navigationBars.getBottom(this).toDp() / 2).coerceAtLeast(8.dp)
    }
    val placeholder = when {
        interactionLocked && action == ComposerAction.Stop -> "Generation in motion..."
        !modelReady -> "Ask anything..."
        else -> "Ask anything..."
    }

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = dockBottomPadding),
        tonalColor = ChatSurfaceLow.copy(alpha = 0.72f),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ChatSurfaceLowest.copy(alpha = 0.9f))
                    .padding(start = 17.dp, top = 11.dp, end = 56.dp, bottom = 11.dp)
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 28.dp),
                    enabled = modelReady && !interactionLocked,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(ChatPrimary),
                    singleLine = false,
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                when (action) {
                    ComposerAction.Send -> SendActionButton(
                        onClick = onPrimaryAction,
                        enabled = value.isNotBlank() && modelReady && !interactionLocked,
                    )

                    ComposerAction.Stop -> GeneratingActionButton(
                        onClick = onPrimaryAction,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SendActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "send_action_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(30.dp)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.46f),
            modifier = Modifier.size(19.dp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EditorialActionButton(
    text: String,
    onClick: () -> Unit,
    filled: Boolean,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "editorial_action_scale"
    )
    val brush = remember {
        Brush.linearGradient(colors = listOf(ChatPrimary, ChatPrimaryContainer))
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (filled && enabled) brush else Brush.linearGradient(listOf(Color(0xFF232327), Color(0xFF232327)))
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .then(
                if (!filled) {
                    Modifier.drawWithCache {
                        onDrawBehind {
                            drawRoundRect(
                                color = ChatOutlineVariant.copy(alpha = 0.24f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                            )
                        }
                    }
                } else Modifier
            )
            .padding(horizontal = if (text.isEmpty()) 18.dp else 22.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            icon()
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (filled && enabled) ChatObsidian else ChatPrimary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeneratingActionButton(
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "generation_button")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "generation_button_rotation"
    )
    val glow by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "generation_button_glow"
    )

    Box(
        modifier = Modifier
            .size(46.dp)
            .combinedClickable(onClick = onClick)
            .drawWithCache {
                val sweep = Brush.sweepGradient(
                    colors = listOf(
                        ChatPrimary.copy(alpha = 0.1f),
                        ChatPrimary.copy(alpha = glow),
                        ChatPrimaryContainer.copy(alpha = 0.55f),
                        ChatPrimary.copy(alpha = 0.1f)
                    )
                )
                onDrawBehind {
                    rotate(rotation) {
                        drawCircle(
                            brush = sweep,
                            radius = size.minDimension / 2f
                        )
                    }
                    drawCircle(
                        color = Color(0xFF2B2B30),
                        radius = size.minDimension / 2f - 5.dp.toPx()
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(Color.White, RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    tonalColor: Color,
    shape: RoundedCornerShape = RoundedCornerShape(28.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = tonalColor,
        border = BorderStroke(1.dp, ChatOutlineVariant.copy(alpha = 0.15f)),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
private fun GlassIsland(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    tonalColor: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = tonalColor,
        border = BorderStroke(1.dp, ChatOutlineVariant.copy(alpha = 0.14f)),
        shadowElevation = 4.dp
    ) {
        content()
    }
}

@Composable
private fun StreamingMessageBubble(
    text: String,
    isThinking: Boolean
) {
    val thinkingLabel = rememberThinkingLabel(isThinking)
    val thinkingAlpha by animateFloatAsState(
        targetValue = if (isThinking) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "thinking_alpha"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isThinking) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label = "stream_content_alpha"
    )

    EditorialMessageContainer(
        isUser = false,
        label = null
    ) {
        if (isThinking || text.isEmpty()) {
            Text(
                text = thinkingLabel,
                modifier = Modifier.graphicsLayer { alpha = thinkingAlpha },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (text.isNotEmpty()) {
            MarkdownMessageContent(
                text = text,
                showCursor = true,
                baseColor = MaterialTheme.colorScheme.onSurface,
                headingColor = ChatPrimary.copy(alpha = 0.94f),
                modifier = Modifier.graphicsLayer { alpha = contentAlpha }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    entryDelayMs: Long = 0L,
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val density = LocalDensity.current
    var isVisible by rememberSaveable(message.id) { mutableStateOf(false) }
    val isUser = message.isUser
    val textColor = if (isUser) ChatObsidian else MaterialTheme.colorScheme.onSurface
    val messageTextStyle = if (isUser) {
        MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    val entryAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.84f),
        label = "message_entry_alpha"
    )
    val entryScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.97f,
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.84f),
        label = "message_entry_scale"
    )
    val entryTranslation by animateFloatAsState(
        targetValue = if (isVisible) 0f else with(density) { 14.dp.toPx() },
        animationSpec = spring(stiffness = 300f, dampingRatio = 0.84f),
        label = "message_entry_translation"
    )

    LaunchedEffect(message.id) {
        if (!isVisible) {
            delay(20 + entryDelayMs)
            isVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entryAlpha
                scaleX = entryScale
                scaleY = entryScale
                translationY = entryTranslation
            }
    ) {
        EditorialMessageContainer(
            isUser = isUser,
            label = null,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(message.text))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            )
        ) {
            SelectionContainer {
                MarkdownMessageContent(
                    text = message.text,
                    showCursor = false,
                    baseColor = textColor,
                    textStyle = messageTextStyle,
                    headingColor = if (isUser) {
                        ChatObsidian.copy(alpha = 0.88f)
                    } else {
                        ChatPrimary.copy(alpha = 0.92f)
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorialMessageContainer(
    isUser: Boolean,
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = modifier
                .widthIn(max = 338.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    if (isUser) {
                        Brush.linearGradient(
                            colors = listOf(ChatPrimaryContainer, ChatPrimary.copy(alpha = 0.92f))
                        )
                    } else {
                        Brush.linearGradient(
                            colors = listOf(ChatSurfaceLow.copy(alpha = 0.96f), ChatSurfaceHigh.copy(alpha = 0.78f))
                        )
                    }
                )
                .drawWithCache {
                    val strokeColor = if (isUser) Color.White.copy(alpha = 0.08f) else ChatOutlineVariant.copy(alpha = 0.16f)
                    val topGlow = if (isUser) {
                        Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.18f), Color.Transparent),
                            radius = size.maxDimension * 0.9f
                        )
                    } else {
                        Brush.linearGradient(colors = listOf(ChatPrimary.copy(alpha = 0.12f), Color.Transparent))
                    }
                    onDrawWithContent {
                        drawRoundRect(topGlow)
                        drawContent()
                        drawRoundRect(
                            color = strokeColor,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx(), 28.dp.toPx())
                        )
                    }
                }
                .padding(horizontal = 18.dp, vertical = 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (label == null) 0.dp else 12.dp)) {
                if (label != null) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) ChatObsidian.copy(alpha = 0.68f) else ChatPrimary
                    )
                }
                content()
            }
        }
    }
}

@Composable
private fun MotionIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1f,
        animationSpec = tween(durationMillis = if (isPressed && enabled) 80 else 120),
        label = "icon_button_scale"
    )

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

@Composable
private fun rememberThinkingLabel(isThinking: Boolean): String {
    var frame by remember(isThinking) { mutableStateOf(0) }

    LaunchedEffect(isThinking) {
        if (!isThinking) {
            frame = 0
            return@LaunchedEffect
        }

        val sequence = listOf(".", "..", "...", "....", "...", "..", ".")
        while (true) {
            delay(180)
            frame = (frame + 1) % sequence.size
        }
    }

    val sequence = listOf(".", "..", "...", "....", "...", "..", ".")
    return "Thinking${sequence[frame]}"
}
