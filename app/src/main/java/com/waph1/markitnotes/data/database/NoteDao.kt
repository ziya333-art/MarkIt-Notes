package com.waph1.markitnotes.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query(
        "SELECT filePath, fileName, folder, title, contentPreview, substr(contentPreview, 1, 500) AS content, lastModifiedMs, color, reminder, isPinned, isArchived, isTrashed FROM notes WHERE isTrashed = 0 AND isArchived = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getAllActiveNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, fileName, folder, title, contentPreview, substr(contentPreview, 1, 500) AS content, lastModifiedMs, color, reminder, isPinned, isArchived, isTrashed FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getAllNotesWithArchive(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, fileName, folder, title, contentPreview, substr(contentPreview, 1, 500) AS content, lastModifiedMs, color, reminder, isPinned, isArchived, isTrashed FROM notes WHERE isTrashed = 1 ORDER BY lastModifiedMs DESC",
    )
    fun getTrashedNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, fileName, folder, title, contentPreview, substr(contentPreview, 1, 500) AS content, lastModifiedMs, color, reminder, isPinned, isArchived, isTrashed FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY lastModifiedMs DESC",
    )
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query(
        "SELECT filePath, fileName, folder, title, contentPreview, substr(contentPreview, 1, 500) AS content, lastModifiedMs, color, reminder, isPinned, isArchived, isTrashed FROM notes WHERE folder = :folder AND isTrashed = 0 ORDER BY isPinned DESC, lastModifiedMs DESC",
    )
    fun getNotesByFolder(folder: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE filePath = :filePath")
    suspend fun getNoteByPath(filePath: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE filePath IN (:filePaths)")
    suspend fun getNotesByPaths(filePaths: List<String>): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE filePath = :filePath")
    suspend fun deleteNoteByPath(filePath: String)

    @Query("DELETE FROM notes")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT folder FROM notes WHERE isTrashed = 0 AND isArchived = 0 AND folder != 'Inbox'")
    fun getAllLabels(): Flow<List<String>>

    @Query("UPDATE notes SET color = :color WHERE filePath = :filePath")
    suspend fun updateColor(
        filePath: String,
        color: Long,
    )

    @Query("UPDATE notes SET isPinned = :isPinned WHERE filePath = :filePath")
    suspend fun updatePinStatus(
        filePath: String,
        isPinned: Boolean,
    )

    @Query("UPDATE notes SET isPinned = :isPinned WHERE filePath IN (:filePaths)")
    suspend fun updatePinStatuses(
        filePaths: List<String>,
        isPinned: Boolean,
    )

    @Query("UPDATE notes SET isArchived = 1, isTrashed = 0 WHERE filePath = :filePath")
    suspend fun archiveNote(filePath: String)

    @Query("UPDATE notes SET isArchived = 1, isTrashed = 0 WHERE filePath IN (:filePaths)")
    suspend fun archiveNotes(filePaths: List<String>)

    @Query("UPDATE notes SET isTrashed = 1 WHERE filePath = :filePath")
    suspend fun trashNote(filePath: String)

    @Query("UPDATE notes SET isTrashed = 1 WHERE filePath IN (:filePaths)")
    suspend fun trashNotes(filePaths: List<String>)

    @Query("UPDATE notes SET isArchived = 0, isTrashed = 0 WHERE filePath = :filePath")
    suspend fun restoreNote(filePath: String)

    @Query("SELECT COUNT(*) FROM notes WHERE folder = :folder")
    suspend fun countNotesInFolder(folder: String): Int

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun deleteAllTrashed()

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesSync(): List<NoteEntity>

    @Query("DELETE FROM notes WHERE filePath IN (:filePaths)")
    suspend fun deleteNotesByPaths(filePaths: List<String>)
}
