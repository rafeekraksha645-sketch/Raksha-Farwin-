package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalNexoraColors
import com.example.ui.theme.NeonCyan
import kotlinx.coroutines.delay

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletItem(val marker: String, val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
}

@Composable
fun NexoraMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    baseTextStyle: TextStyle = MaterialTheme.typography.bodyLarge
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    SelectionContainer(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val headingStyle = when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        }
                        Text(
                            text = parseInlineFormatting(
                                block.text,
                                textColor,
                                MaterialTheme.colorScheme.primary
                            ),
                            style = headingStyle.copy(color = textColor, fontWeight = FontWeight.Bold)
                        )
                    }

                    is MarkdownBlock.Paragraph -> {
                        Text(
                            text = parseInlineFormatting(
                                block.text,
                                textColor,
                                MaterialTheme.colorScheme.primary
                            ),
                            style = baseTextStyle.copy(color = textColor)
                        )
                    }

                    is MarkdownBlock.BulletItem -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = block.marker,
                                style = baseTextStyle.copy(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = parseInlineFormatting(
                                    block.text,
                                    textColor,
                                    MaterialTheme.colorScheme.primary
                                ),
                                style = baseTextStyle.copy(color = textColor),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    is MarkdownBlock.CodeBlock -> {
                        SyntaxHighlightedCodeBlock(
                            language = block.language,
                            code = block.code
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyntaxHighlightedCodeBlock(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val nexoraColors = LocalNexoraColors.current
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1800L)
            copied = false
        }
    }

    val highlighted = remember(code, language) { highlightSyntax(code) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(nexoraColors.codeBackground)
            .border(
                width = 1.dp,
                color = NeonCyan.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(nexoraColors.codeHeaderBackground)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = "Code block language",
                    tint = NeonCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color(0xFFBAE6FD),
                        letterSpacing = 0.8.sp
                    )
                )
            }
            IconButton(
                onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                },
                modifier = Modifier
                    .size(32.dp)
                    .testTag("copy_code_button")
            ) {
                Icon(
                    imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                    contentDescription = if (copied) "Code copied" else "Copy code",
                    tint = if (copied) Color(0xFF34D399) else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = highlighted,
                style = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFFE2E8F0)
                )
            )
        }
    }
}

private fun parseMarkdownBlocks(input: String): List<MarkdownBlock> {
    val lines = input.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraphBuffer = StringBuilder()

    fun flushParagraph() {
        val text = paragraphBuffer.toString().trim()
        if (text.isNotEmpty()) {
            blocks.add(MarkdownBlock.Paragraph(text))
        }
        paragraphBuffer.clear()
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            flushParagraph()
            val lang = trimmed.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
            i++
            continue
        }

        when {
            trimmed.isEmpty() -> flushParagraph()
            trimmed.startsWith("### ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim()))
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim()))
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim()))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                flushParagraph()
                blocks.add(MarkdownBlock.BulletItem("•", trimmed.substring(2).trim()))
            }
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                flushParagraph()
                val dotIdx = trimmed.indexOf('.')
                val marker = trimmed.substring(0, dotIdx + 1)
                val body = trimmed.substring(dotIdx + 1).trim()
                blocks.add(MarkdownBlock.BulletItem(marker, body))
            }
            else -> {
                if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append("\n")
                paragraphBuffer.append(line)
            }
        }
        i++
    }
    flushParagraph()
    return blocks
}

private fun parseInlineFormatting(
    text: String,
    baseColor: Color,
    accentColor: Color
): AnnotatedString = buildAnnotatedString {
    var idx = 0
    while (idx < text.length) {
        when {
            text.startsWith("**", idx) -> {
                val end = text.indexOf("**", idx + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                        append(text.substring(idx + 2, end))
                    }
                    idx = end + 2
                } else {
                    append("**")
                    idx += 2
                }
            }
            text.startsWith("`", idx) -> {
                val end = text.indexOf("`", idx + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 13.sp,
                            color = accentColor,
                            background = accentColor.copy(alpha = 0.14f)
                        )
                    ) {
                        append(" ${text.substring(idx + 1, end)} ")
                    }
                    idx = end + 1
                } else {
                    append("`")
                    idx += 1
                }
            }
            text.startsWith("*", idx) && !text.startsWith("**", idx) -> {
                val end = text.indexOf("*", idx + 1)
                if (end != -1 && end > idx + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor)) {
                        append(text.substring(idx + 1, end))
                    }
                    idx = end + 1
                } else {
                    append("*")
                    idx += 1
                }
            }
            else -> {
                append(text[idx])
                idx++
            }
        }
    }
}

private val CODE_KEYWORDS = setOf(
    "fun", "val", "var", "class", "object", "interface", "return", "if", "else", "when", "for", "while",
    "import", "package", "def", "async", "await", "function", "const", "let", "export", "from",
    "public", "private", "protected", "static", "void", "int", "string", "boolean", "true", "false",
    "null", "None", "SELECT", "FROM", "WHERE", "JOIN", "INSERT", "UPDATE", "DELETE", "CREATE",
    "TABLE", "GROUP", "BY", "ORDER", "try", "catch", "throw", "struct", "impl", "fn", "mut", "pub",
    "match", "suspend", "sealed", "data"
)

private fun highlightSyntax(code: String): AnnotatedString = buildAnnotatedString {
    val lines = code.split("\n")
    lines.forEachIndexed { lineIdx, line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("//") || trimmed.startsWith("#") || trimmed.startsWith("--")) {
            withStyle(SpanStyle(color = Color(0xFF64748B), fontStyle = FontStyle.Italic)) {
                append(line)
            }
        } else {
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                when {
                    ch == '"' || ch == '\'' -> {
                        val quote = ch
                        var end = i + 1
                        while (end < line.length && line[end] != quote) {
                            if (line[end] == '\\' && end + 1 < line.length) end++
                            end++
                        }
                        if (end < line.length) end++
                        withStyle(SpanStyle(color = Color(0xFF34D399))) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    ch.isLetter() || ch == '_' -> {
                        var end = i + 1
                        while (end < line.length && (line[end].isLetterOrDigit() || line[end] == '_')) {
                            end++
                        }
                        val word = line.substring(i, end)
                        if (word in CODE_KEYWORDS) {
                            withStyle(SpanStyle(color = Color(0xFF38BDF8), fontWeight = FontWeight.SemiBold)) {
                                append(word)
                            }
                        } else if (word.firstOrNull()?.isUpperCase() == true) {
                            withStyle(SpanStyle(color = Color(0xFFC084FC))) {
                                append(word)
                            }
                        } else {
                            withStyle(SpanStyle(color = Color(0xFFE2E8F0))) {
                                append(word)
                            }
                        }
                        i = end
                    }
                    ch.isDigit() -> {
                        var end = i + 1
                        while (end < line.length && (line[end].isDigit() || line[end] == '.')) {
                            end++
                        }
                        withStyle(SpanStyle(color = Color(0xFFFBBF24))) {
                            append(line.substring(i, end))
                        }
                        i = end
                    }
                    else -> {
                        withStyle(SpanStyle(color = Color(0xFF94A3B8))) {
                            append(ch)
                        }
                        i++
                    }
                }
            }
        }
        if (lineIdx < lines.lastIndex) append("\n")
    }
}
