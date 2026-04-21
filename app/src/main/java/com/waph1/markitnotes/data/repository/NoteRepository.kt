package com.waph1.markitnotes.data.repository

import com.waph1.markitnotes.data.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getAllNotes(): Flow<List<Note>>

    fun getAllNotesWithArchive(): Flow<List<Note>>

    suspend fun getNote(id: String): Note?

    suspend fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
    ): String

    suspend fun deleteNote(id: String)

    suspend fun deleteNotes(noteIds: List<String>)

    suspend fun archiveNote(id: String)

    suspend fun archiveNotes(noteIds: List<String>)

    suspend fun setNoteColor(
        id: String,
        color: Long,
    )

    suspend fun togglePinStatus(
        noteIds: List<String>,
        isPinned: Boolean,
    )

    suspend fun restoreNote(id: String)

    suspend fun moveNotes(
        notes: List<Note>,
        targetFolder: String,
    )

    suspend fun setRootFolder(uriString: String)

    fun getLabels(): Flow<List<String>>

    suspend fun createLabel(name: String): Boolean

    suspend fun deleteLabel(name: String): Boolean

    suspend fun emptyTrash()

    suspend fun refreshNotes()
}
