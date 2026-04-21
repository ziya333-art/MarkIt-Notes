package com.waph1.markitnotes.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a Note.
 * This is the "cached" version of what's on disk.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    // Unique ID: "Label/filename.md"
    @PrimaryKey
    val filePath: String,
    // "MyNote.md"
    val fileName: String,
    // "Inbox", "Work", etc.
    val folder: String,
    val title: String,
    // First ~200 chars for dashboard
    val contentPreview: String,
    // Full content
    val content: String,
    // Timestamp in millis
    val lastModifiedMs: Long,
    val color: Long,
    val reminder: Long? = null,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isTrashed: Boolean,
)
