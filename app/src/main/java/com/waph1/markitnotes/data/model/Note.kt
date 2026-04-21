package com.waph1.markitnotes.data.model

import java.io.File
import java.util.Date

data class Note(
    val file: File,
    val title: String,
    val content: String,
    val lastModified: Date,
    val color: Long,
    val reminder: Long? = null,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
) {
    val folder: String
        get() = file.parent ?: "Unknown"

    val id: String
        get() = file.path
}
