package com.personalai.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private sealed class MarkdownBlock {
    data class Paragraph(val content: String) : MarkdownBlock()
    data class ListItem(val content: String) : MarkdownBlock()
    data class Code(val content: String) : MarkdownBlock()
}

/**
 * Minimal markdown renderer for chat bubbles: fenced code blocks, bullet/numbered lists, and
 * inline bold, italic, and inline code. Deliberately not a general-purpose markdown library —
 * just enough to keep the assistant's lists and code snippets readable instead of one unbroken line.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Text(
                        text = block.content,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .background(color.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                    )
                }
                is MarkdownBlock.ListItem -> {
                    Row {
                        Text("•  ", color = color, style = MaterialTheme.typography.bodyMedium)
                        Text(renderInline(block.content), color = color, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(renderInline(block.content), color = color, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private val bulletRegex = Regex("""^\s*[-*]\s+(.*)""")
private val numberedRegex = Regex("""^\s*\d+\.\s+(.*)""")

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val lines = text.lines()
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.trimStart().startsWith("```")) {
            i++
            val codeLines = mutableListOf<String>()
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines += lines[i]
                i++
            }
            i++ // skip the closing fence, if any
            blocks += MarkdownBlock.Code(codeLines.joinToString("\n"))
            continue
        }
        when {
            line.isBlank() -> Unit
            bulletRegex.matches(line) -> blocks += MarkdownBlock.ListItem(bulletRegex.find(line)!!.groupValues[1])
            numberedRegex.matches(line) -> blocks += MarkdownBlock.ListItem(numberedRegex.find(line)!!.groupValues[1])
            else -> blocks += MarkdownBlock.Paragraph(line)
        }
        i++
    }
    return blocks
}

private fun renderInline(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end == -1) {
                    append(text.substring(i))
                    i = text.length
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            (text[i] == '*' || text[i] == '_') && i + 1 < text.length && !text[i + 1].isWhitespace() -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end == -1) {
                    append(text[i])
                    i++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(text.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
