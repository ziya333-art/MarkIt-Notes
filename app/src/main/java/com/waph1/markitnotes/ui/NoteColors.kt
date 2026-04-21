package com.waph1.markitnotes.ui

/**
 * Shared color palette used across the app (card backgrounds, color pickers, etc.).
 * Colors are stored as ARGB Long values to match the [Note.color] field type.
 */
object NoteColors {
    val palette: List<Long> =
        listOf(
            0xFFFFFFFF, 0xFFF28B82, 0xFFFBBC04, 0xFFFFF475,
            0xFFCCFF90, 0xFFA7FFEB, 0xFFCBF0F8, 0xFFAECBFA,
            0xFFD7AEFB, 0xFFFDCFE8, 0xFFE6C9A8, 0xFFE8EAED,
        )
}
