package com.waph1.markitnotes.data.utils

import com.waph1.markitnotes.data.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteFormatUtils {
    data class FrontMatterData(val color: Long, val reminder: Long?, val cleanContent: String)

    /**
     * Universal Parser: Robustly handles quoted and unquoted values.
     */
    fun parseFrontMatter(rawContent: String): FrontMatterData {
        val lines = rawContent.lines()
        if (lines.size < 3 || lines[0].trim() != "---") {
            return FrontMatterData(0xFFFFFFFF, null, rawContent)
        }

        val closingIndexInDropped = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (closingIndexInDropped == -1) {
            return FrontMatterData(0xFFFFFFFF, null, rawContent)
        }

        val actualClosingIndex = closingIndexInDropped + 1
        val yamlLines = lines.subList(1, actualClosingIndex)

        var contentStartIndex = actualClosingIndex + 1
        while (contentStartIndex < lines.size && lines[contentStartIndex].isBlank()) {
            contentStartIndex++
        }

        val cleanContent =
            if (contentStartIndex < lines.size) {
                lines.subList(contentStartIndex, lines.size).joinToString("\n")
            } else {
                ""
            }

        var color = 0xFFFFFFFF
        var reminder: Long? = null
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        yamlLines.forEach { line ->
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                // Robust removal of YAML quotes
                val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("\u0027")
                when (key) {
                    "color" -> {
                        try {
                            if (value.startsWith("#")) {
                                val hex = value.substring(1)
                                var parsedColor = hex.toLong(16)
                                if (hex.length == 6) parsedColor = parsedColor or 0xFF000000L
                                color = parsedColor
                            } else {
                                color = value.toLong()
                            }
                        } catch (e: Exception) {
                        }
                    }
                    "reminder" -> {
                        try {
                            reminder = dateFormat.parse(value)?.time
                        } catch (e: Exception) {
                            reminder = value.toLongOrNull()
                        }
                    }
                }
            }
        }

        return FrontMatterData(color, reminder, cleanContent)
    }

    fun constructFileContent(
        note: Note,
        existingRawContent: String? = null,
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val newColor = String.format("#%08X", note.color)
        val newReminder = note.reminder?.let { dateFormat.format(Date(it)) }

        if (existingRawContent == null || !existingRawContent.trimStart().startsWith("---")) {
            return buildString {
                append("---\n")
                append("color: \"$newColor\"\n")
                newReminder?.let { append("reminder: \"$it\"\n") }
                append("---\n\n")
                append(note.content)
            }
        }

        val lines = existingRawContent.lines().toMutableList()
        val closingIndex = lines.drop(1).indexOfFirst { it.trim() == "---" } + 1
        if (closingIndex <= 0) return existingRawContent

        val yamlLines = lines.subList(1, closingIndex).toMutableList()

        var colorFound = false
        var reminderFound = false

        for (i in yamlLines.indices) {
            val line = yamlLines[i]
            val key = line.substringBefore(":").trim()
            if (key == "color") {
                yamlLines[i] = "color: \"$newColor\""
                colorFound = true
            } else if (key == "reminder") {
                if (newReminder != null) {
                    yamlLines[i] = "reminder: \"$newReminder\""
                } else {
                    yamlLines[i] = ""
                }
                reminderFound = true
            }
        }

        if (!colorFound) yamlLines.add("color: \"$newColor\"")
        if (!reminderFound && newReminder != null) yamlLines.add("reminder: \"$newReminder\"")

        val cleanedYaml = yamlLines.filter { it.isNotBlank() }.joinToString("\n")

        return "---\n$cleanedYaml\n---\n\n${note.content}"
    }
}
