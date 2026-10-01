package com.monicauditya.june.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monicauditya.june.R
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    val start: Int

    data class Heading(
        val level: Int,
        val text: String,
        override val start: Int,
    ) : MarkdownBlock

    data class Paragraph(
        val text: String,
        override val start: Int,
    ) : MarkdownBlock

    data class ListBlock(
        val items: List<String>,
        override val start: Int,
    ) : MarkdownBlock

    data class CodeBlock(
        val code: String,
        override val start: Int,
    ) : MarkdownBlock
}

@Composable
fun MarkdownMessageContent(
    text: String,
    showCursor: Boolean,
    baseColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    headingColor: Color = baseColor.copy(alpha = 0.76f),
    modifier: Modifier = Modifier,
) {
    val blocks = rememberMarkdownBlocks(text)
    val cursorAlpha = rememberStreamingCursorAlpha(showCursor)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val isLastBlock = index == blocks.lastIndex
            when (block) {
                is MarkdownBlock.Heading -> MarkdownHeading(
                    block = block,
                    showCursor = showCursor && isLastBlock,
                    color = headingColor,
                    cursorAlpha = cursorAlpha
                )

                is MarkdownBlock.Paragraph -> MarkdownParagraph(
                    text = block.text,
                    showCursor = showCursor && isLastBlock,
                    baseColor = baseColor,
                    textStyle = textStyle,
                    cursorAlpha = cursorAlpha
                )

                is MarkdownBlock.ListBlock -> MarkdownList(
                    items = block.items,
                    showCursor = showCursor && isLastBlock,
                    baseColor = baseColor,
                    textStyle = textStyle,
                    cursorAlpha = cursorAlpha
                )

                is MarkdownBlock.CodeBlock -> MarkdownCodeBlock(
                    code = block.code,
                    showCursor = showCursor && isLastBlock,
                    cursorAlpha = cursorAlpha
                )
            }
        }

        if (blocks.isEmpty() && showCursor) {
            Text(
                text = buildCursorAnnotatedString(
                    text = "",
                    showCursor = true,
                    cursorAlpha = cursorAlpha
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun MarkdownHeading(
    block: MarkdownBlock.Heading,
    showCursor: Boolean,
    color: Color,
    cursorAlpha: Float,
) {
    val style = when (block.level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
    }

    InlineCursorText(
        text = remember(block.text, showCursor, cursorAlpha) {
            buildCursorAnnotatedString(
                text = buildStyledText(block.text),
                showCursor = showCursor,
                cursorAlpha = cursorAlpha
            )
        },
        showCursor = showCursor,
        textStyle = style,
        color = color
    )
}

@Composable
private fun MarkdownParagraph(
    text: String,
    showCursor: Boolean,
    baseColor: Color,
    textStyle: TextStyle,
    cursorAlpha: Float,
) {
    InlineCursorText(
        text = remember(text, showCursor, cursorAlpha) {
            buildCursorAnnotatedString(
                text = buildStyledText(text),
                showCursor = showCursor,
                cursorAlpha = cursorAlpha
            )
        },
        showCursor = showCursor,
        textStyle = textStyle,
        color = baseColor
    )
}

@Composable
private fun MarkdownList(
    items: List<String>,
    showCursor: Boolean,
    baseColor: Color,
    textStyle: TextStyle,
    cursorAlpha: Float,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "\u2022",
                    color = baseColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                InlineCursorText(
                    text = remember(item, showCursor, index, cursorAlpha) {
                        buildCursorAnnotatedString(
                            text = buildStyledText(item),
                            showCursor = showCursor && index == items.lastIndex,
                            cursorAlpha = cursorAlpha
                        )
                    },
                    showCursor = showCursor && index == items.lastIndex,
                    textStyle = textStyle,
                    color = baseColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    showCursor: Boolean,
    cursorAlpha: Float,
) {
    val codeFont = remember {
        FontFamily(Font(R.font.jetbrains_mono))
    }
    val highlightedCode = remember(code, showCursor, cursorAlpha) {
        buildCursorAnnotatedString(
            text = buildHighlightedCodeString(code),
            showCursor = showCursor,
            cursorAlpha = cursorAlpha
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            CopyActionButton(
                textToCopy = code,
                label = "Copy code",
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = highlightedCode,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                color = Color(0xFFE5E7EB),
                fontFamily = codeFont,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun InlineCursorText(
    text: AnnotatedString,
    showCursor: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = textStyle,
        color = color,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun rememberStreamingCursorAlpha(showCursor: Boolean): Float {
    if (!showCursor) return 1f

    val transition = rememberInfiniteTransition(label = "markdown_stream_cursor")
    return transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "markdown_stream_cursor_alpha"
    ).value
}

private fun buildCursorAnnotatedString(
    text: String,
    showCursor: Boolean,
    cursorAlpha: Float,
): AnnotatedString = buildCursorAnnotatedString(
    text = AnnotatedString(text),
    showCursor = showCursor,
    cursorAlpha = cursorAlpha
)

private fun buildCursorAnnotatedString(
    text: AnnotatedString,
    showCursor: Boolean,
    cursorAlpha: Float,
): AnnotatedString = buildAnnotatedString {
    append(text)
    if (showCursor) {
        pushStyle(
            SpanStyle(
                color = Color.Unspecified.copy(alpha = cursorAlpha)
            )
        )
        append("\u258d")
        pop()
    }
}

@Composable
private fun CopyActionButton(
    textToCopy: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember(textToCopy) { mutableStateOf(false) }
    var pressed by remember(textToCopy) { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = when {
            pressed -> 0.64f
            copied -> 1f
            else -> 0.8f
        },
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "copy_feedback_alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "copy_feedback_scale"
    )

    LaunchedEffect(copied, pressed) {
        if (pressed) {
            delay(140)
            pressed = false
        }
        if (copied) {
            delay(900)
            copied = false
        }
    }

    IconButton(
        onClick = {
            pressed = true
            clipboardManager.setText(AnnotatedString(textToCopy))
            copied = true
        },
        modifier = modifier
            .size(28.dp)
            .scale(scale)
            .alpha(alpha)
    ) {
        Icon(
            imageVector = if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = label,
            tint = if (copied) Color(0xFF93C5FD) else Color(0xFF9CA3AF),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun rememberMarkdownBlocks(text: String): List<MarkdownBlock> {
    val cache = remember { MarkdownParseCache() }
    return remember(text) { cache.parse(text) }
}

private class MarkdownParseCache {
    private var previousText: String = ""
    private var previousBlocks: List<MarkdownBlock> = emptyList()

    fun parse(raw: String): List<MarkdownBlock> {
        val normalized = raw.replace("\r\n", "\n")
        if (normalized == previousText) return previousBlocks

        val parsed = if (
            previousText.isNotEmpty() &&
            normalized.startsWith(previousText) &&
            previousBlocks.isNotEmpty()
        ) {
            val prefixBlocks = previousBlocks.dropLast(1)
            val reparsedStart = previousBlocks.last().start
            prefixBlocks + parseMarkdown(
                raw = normalized.substring(reparsedStart),
                baseOffset = reparsedStart
            )
        } else {
            parseMarkdown(normalized)
        }

        previousText = normalized
        previousBlocks = parsed
        return parsed
    }
}

private fun parseMarkdown(
    raw: String,
    baseOffset: Int = 0,
): List<MarkdownBlock> {
    if (raw.isBlank()) return emptyList()

    val blocks = mutableListOf<MarkdownBlock>()
    val lines = raw.split('\n')
    val lineStarts = IntArray(lines.size)
    var runningOffset = 0
    lines.forEachIndexed { index, line ->
        lineStarts[index] = runningOffset
        runningOffset += line.length + if (index == lines.lastIndex) 0 else 1
    }
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val lineStart = baseOffset + lineStarts[index]

        if (line.isBlank()) {
            index++
            continue
        }

        if (line.startsWith("```")) {
            index++
            val codeLines = mutableListOf<String>()
            while (index < lines.size && !lines[index].startsWith("```")) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size && lines[index].startsWith("```")) {
                index++
            }
            blocks += MarkdownBlock.CodeBlock(
                code = codeLines.joinToString("\n"),
                start = lineStart
            )
            continue
        }

        val headingMatch = Regex("^(#{1,3})\\s+(.*)$").find(line)
        if (headingMatch != null) {
            blocks += MarkdownBlock.Heading(
                level = headingMatch.groupValues[1].length,
                text = headingMatch.groupValues[2],
                start = lineStart
            )
            index++
            continue
        }

        if (line.matches(Regex("^\\s*([-*]|\\d+\\.)\\s+.*$"))) {
            val listStart = lineStart
            val listItems = mutableListOf<String>()
            while (index < lines.size && lines[index].matches(Regex("^\\s*([-*]|\\d+\\.)\\s+.*$"))) {
                listItems += lines[index].replaceFirst(Regex("^\\s*([-*]|\\d+\\.)\\s+"), "")
                index++
            }
            blocks += MarkdownBlock.ListBlock(
                items = listItems,
                start = listStart
            )
            continue
        }

        val paragraphStart = lineStart
        val paragraphLines = mutableListOf<String>()
        while (index < lines.size) {
            val current = lines[index]
            val startsSpecialBlock =
                current.isBlank() ||
                    current.startsWith("```") ||
                    current.matches(Regex("^(#{1,3})\\s+.*$")) ||
                    current.matches(Regex("^\\s*([-*]|\\d+\\.)\\s+.*$"))

            if (startsSpecialBlock) break

            paragraphLines += current
            index++
        }
        blocks += MarkdownBlock.Paragraph(
            text = paragraphLines.joinToString("\n"),
            start = paragraphStart
        )
    }

    return blocks
}

private fun buildStyledText(text: String): AnnotatedString = buildAnnotatedString {
    val inlineCodeBackground = Color(0x14FFFFFF)
    var index = 0

    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", startIndex = index + 2)
                if (end != -1) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(text.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '*' -> {
                val end = text.indexOf('*', startIndex = index + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            text[index] == '`' -> {
                val end = text.indexOf('`', startIndex = index + 1)
                if (end != -1) {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily(Font(R.font.jetbrains_mono)),
                            background = inlineCodeBackground,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(text.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(text[index])
                    index++
                }
            }

            else -> {
                append(text[index])
                index++
            }
        }
    }
}

private fun buildHighlightedCodeString(code: String): AnnotatedString = buildAnnotatedString {
    val keywordStyle = SpanStyle(color = Color(0xFF7DD3FC))
    val declarationStyle = SpanStyle(color = Color(0xFFC4B5FD))
    val typeStyle = SpanStyle(color = Color(0xFFF9A8D4))
    val stringStyle = SpanStyle(color = Color(0xFFFDE68A))
    val commentStyle = SpanStyle(color = Color(0xFF94A3B8))
    val numberStyle = SpanStyle(color = Color(0xFFFCA5A5))
    val builtinStyle = SpanStyle(color = Color(0xFF67E8F9))
    val decoratorStyle = SpanStyle(color = Color(0xFFF0ABFC))
    val keywordPattern = Regex("\\b(fun|val|var|if|else|return|class|object|data|when|for|while|private|public|internal|suspend|import|package|true|false|null|def|elif|in|is|not|and|or|from|as|try|except|finally|with|yield|lambda|break|continue|pass)\\b")
    val declarationPattern = Regex("\\b(?:fun|class|object|def)\\s+([A-Za-z_][A-Za-z0-9_]*)")
    val typePattern = Regex("\\b(Int|Long|Float|Double|String|Boolean|Unit|Any|List|Map|Set|MutableList|MutableMap|MutableSet|Array|Char|Byte|Short|Nothing|str|int|float|bool|list|dict|tuple|set)\\b")
    val stringPattern = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    val commentPattern = Regex("(?m)//.*$|#.*$")
    val numberPattern = Regex("\\b\\d+(?:\\.\\d+)?\\b")
    val builtinPattern = Regex("\\b(print|len|range|enumerate|map|filter|sorted|join|append|lower|upper|trim|println)\\b")
    val decoratorPattern = Regex("(?m)@[A-Za-z_][A-Za-z0-9_]*")

    data class TokenRange(val start: Int, val end: Int, val style: SpanStyle, val priority: Int)

    val tokens = mutableListOf<TokenRange>()

    decoratorPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, decoratorStyle, 6)
    }
    commentPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, commentStyle, 5)
    }
    stringPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, stringStyle, 4)
    }
    keywordPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, keywordStyle, 1)
    }
    declarationPattern.findAll(code).forEach { match ->
        val group = match.groups[1] ?: return@forEach
        tokens += TokenRange(group.range.first, group.range.last + 1, declarationStyle, 2)
    }
    typePattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, typeStyle, 2)
    }
    numberPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, numberStyle, 2)
    }
    builtinPattern.findAll(code).forEach { match ->
        tokens += TokenRange(match.range.first, match.range.last + 1, builtinStyle, 2)
    }

    val sortedTokens = tokens.sortedWith(compareBy<TokenRange> { it.start }.thenByDescending { it.priority })
    var cursor = 0

    sortedTokens.forEach { token ->
        if (token.start < cursor) return@forEach

        append(code.substring(cursor, token.start))
        pushStyle(token.style)
        append(code.substring(token.start, token.end))
        pop()
        cursor = token.end
    }

    if (cursor < code.length) {
        append(code.substring(cursor))
    }
}
