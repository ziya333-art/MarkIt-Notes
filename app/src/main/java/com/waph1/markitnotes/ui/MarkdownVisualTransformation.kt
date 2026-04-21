package com.waph1.markitnotes.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp

class MarkdownVisualTransformation(
    private val isDark: Boolean,
    private val cursorPosition: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            buildAnnotatedStringWithMarkdown(text.text, cursorPosition, isDark),
            OffsetMapping.Identity,
        )
    }
}

private val headingRegex = Regex("""^(#{1,6}\s+)(.*)$""", RegexOption.MULTILINE)
private val boldRegex = Regex("""\*\*([^\s*][^*]*[^\s*]|[^\s*])\*\*""")
private val italicRegex = Regex("""(?<!\*)\*([^\*]+)\*|(?<!_)_([^_]+)_""")
private val strikeRegex = Regex("""~~(.*?)~~""")
private val underlineRegex = Regex("""<u>(.*?)</u>""")
private val codeRegex = Regex("""`(.*?)`""")
private val checkboxRegex = Regex("""^(\s*[-*+]\s+\[[ xX]\]\s+)(.*)$""", RegexOption.MULTILINE)
private val listRegex = Regex("""^(\s*[-*+•]|\d+\.)\s+(.*)$""", RegexOption.MULTILINE)
private val quoteRegex = Regex("""^(\s*>\s+)(.*)$""", RegexOption.MULTILINE)
private val checkboxStatusRegex = Regex("""\[[xX]\]""")

fun buildAnnotatedStringWithMarkdown(
    text: String,
    cursorPosition: Int,
    isDark: Boolean,
): AnnotatedString {
    val syntaxColor = if (isDark) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f)

    return buildAnnotatedString {
        append(text)

        // 1. Headings
        headingRegex.findAll(text).forEach { match ->
            val hashPart = match.groups[1]!!
            val contentPart = match.groups[2]!!

            val isCursorInHeading = cursorPosition in match.range.start..match.range.endInclusive + 1
            val hasContent = contentPart.value.isNotEmpty()

            if (hasContent && (!isCursorInHeading || cursorPosition > hashPart.range.endInclusive + 1)) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), hashPart.range.first, hashPart.range.last + 1)
            } else {
                addStyle(SpanStyle(color = syntaxColor), hashPart.range.first, hashPart.range.last + 1)
            }

            val level = hashPart.value.trim().length
            val fontSize =
                when (level) {
                    1 -> 24.sp
                    2 -> 20.sp
                    3 -> 18.sp
                    else -> 16.sp
                }
            addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = fontSize), contentPart.range.first, contentPart.range.last + 1)
        }

        // 2. Bold
        boldRegex.findAll(text).forEach { match ->
            val start = match.range.start
            val end = match.range.endInclusive + 1
            val contentStart = start + 2
            val contentEnd = end - 2
            val content = match.groups[1]!!.value

            val isCursorAtBoundary = cursorPosition in start..contentStart || cursorPosition in contentEnd..end

            if (content.isNotEmpty() && !isCursorAtBoundary) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), start, contentStart)
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), contentEnd, end)
            } else {
                addStyle(SpanStyle(color = syntaxColor), start, contentStart)
                addStyle(SpanStyle(color = syntaxColor), contentEnd, end)
            }
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), contentStart, contentEnd)
        }

        // 3. Italic
        italicRegex.findAll(text).forEach { match ->
            val start = match.range.start
            val end = match.range.endInclusive + 1
            val contentStart = start + 1
            val contentEnd = end - 1
            val content = match.groups[1]?.value ?: match.groups[2]?.value ?: ""

            val isCursorAtBoundary = cursorPosition == start || cursorPosition == contentStart || cursorPosition == contentEnd || cursorPosition == end

            if (content.isNotEmpty() && !isCursorAtBoundary) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), start, contentStart)
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), contentEnd, end)
            } else {
                addStyle(SpanStyle(color = syntaxColor), start, contentStart)
                addStyle(SpanStyle(color = syntaxColor), contentEnd, end)
            }
            addStyle(SpanStyle(fontStyle = FontStyle.Italic), contentStart, contentEnd)
        }

        // 4. Strikethrough
        strikeRegex.findAll(text).forEach { match ->
            val start = match.range.start
            val end = match.range.endInclusive + 1
            val contentStart = start + 2
            val contentEnd = end - 2
            val content = match.groups[1]!!.value

            val isCursorAtBoundary = cursorPosition in start..contentStart || cursorPosition in contentEnd..end

            if (content.isNotEmpty() && !isCursorAtBoundary) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), start, contentStart)
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), contentEnd, end)
            } else {
                addStyle(SpanStyle(color = syntaxColor), start, contentStart)
                addStyle(SpanStyle(color = syntaxColor), contentEnd, end)
            }
            addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), contentStart, contentEnd)
        }

        // 5. Underline
        underlineRegex.findAll(text).forEach { match ->
            val start = match.range.start
            val end = match.range.endInclusive + 1
            val contentStart = start + 3
            val contentEnd = end - 4
            val content = match.groups[1]!!.value

            val isCursorAtBoundary = cursorPosition in start..contentStart || cursorPosition in contentEnd..end

            if (content.isNotEmpty() && !isCursorAtBoundary) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), start, contentStart)
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), contentEnd, end)
            } else {
                addStyle(SpanStyle(color = syntaxColor), start, contentStart)
                addStyle(SpanStyle(color = syntaxColor), contentEnd, end)
            }
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline), contentStart, contentEnd)
        }

        // 6. Code
        codeRegex.findAll(text).forEach { match ->
            val start = match.range.start
            val end = match.range.endInclusive + 1
            val contentStart = start + 1
            val contentEnd = end - 1
            val content = match.groups[1]!!.value

            val isCursorAtBoundary = cursorPosition in start..contentStart || cursorPosition in contentEnd..end

            if (content.isNotEmpty() && !isCursorAtBoundary) {
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), start, contentStart)
                addStyle(SpanStyle(color = Color.Transparent, fontSize = 0.sp), contentEnd, end)
            } else {
                addStyle(SpanStyle(color = syntaxColor), start, contentStart)
                addStyle(SpanStyle(color = syntaxColor), contentEnd, end)
            }
            addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.08f),
                ),
                contentStart,
                contentEnd,
            )
        }

        // 7. Checkboxes and Lists
        checkboxRegex.findAll(text).forEach { match ->
            val prefixPart = match.groups[1]!!
            val contentPart = match.groups[2]!!
            val isChecked = prefixPart.value.contains(checkboxStatusRegex)

            addStyle(SpanStyle(color = syntaxColor), prefixPart.range.first, prefixPart.range.last + 1)
            if (isChecked) {
                addStyle(
                    SpanStyle(
                        textDecoration = TextDecoration.LineThrough,
                        color = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f),
                    ),
                    contentPart.range.first,
                    contentPart.range.last + 1,
                )
            }
        }

        listRegex.findAll(text).forEach { match ->
            if (match.value.contains("[ ]") || match.value.contains("[x]")) return@forEach
            val prefixPart = match.groups[1]!!
            addStyle(SpanStyle(color = syntaxColor, fontWeight = FontWeight.Bold), prefixPart.range.first, prefixPart.range.last + 1)
        }

        // 8. Blockquotes
        quoteRegex.findAll(text).forEach { match ->
            val prefixPart = match.groups[1]!!
            val contentPart = match.groups[2]!!
            addStyle(SpanStyle(color = syntaxColor), prefixPart.range.first, prefixPart.range.last + 1)
            addStyle(
                SpanStyle(
                    fontStyle = FontStyle.Italic,
                    color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f),
                ),
                contentPart.range.first,
                contentPart.range.last + 1,
            )
        }
    }
}
