package com.waph1.markitnotes.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.waph1.markitnotes.data.database.AppDatabase
import com.waph1.markitnotes.data.database.LabelDao
import com.waph1.markitnotes.data.database.LabelEntity
import com.waph1.markitnotes.data.database.NoteDao
import com.waph1.markitnotes.data.database.NoteEntity
import com.waph1.markitnotes.data.model.AppConfig
import com.waph1.markitnotes.data.model.Note
import com.waph1.markitnotes.data.utils.NoteFormatUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.util.Date

class RoomNoteRepository(
    private val context: Context,
    private val metadataManager: MetadataManager,
) : NoteRepository {
    private val noteDao: NoteDao = AppDatabase.getDatabase(context).noteDao()
    private val labelDao: LabelDao = AppDatabase.getDatabase(context).labelDao()
    private var rootDir: DocumentFile? = null
    private var appConfig = AppConfig()

    private val cacheMutex = Mutex()
    private val contentCache = LinkedHashMap<String, Pair<Long, String>>(64, 0.75f, true)

    override suspend fun setRootFolder(uriString: String) {
        try {
            val uri = Uri.parse(uriString)
            // Validate permission first
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                // Permission might already be granted or not persistable, proceed with caution
                android.util.Log.w("RoomNoteRepository", "Could not take persistable permission: ${e.message}")
            }

            val docFile = DocumentFile.fromTreeUri(context, uri)
            if (docFile == null || !docFile.canRead()) {
                android.util.Log.e("RoomNoteRepository", "Root folder is not readable or null: $uriString")
                return
            }

            rootDir = docFile
            appConfig = metadataManager.loadConfig(docFile)
            refreshNotes()
        } catch (e: Exception) {
            android.util.Log.e("RoomNoteRepository", "Error setting root folder: $uriString", e)
        }
    }

    override fun getAllNotes(): Flow<List<Note>> {
        return noteDao.getAllActiveNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    override fun getAllNotesWithArchive(): Flow<List<Note>> {
        return noteDao.getAllNotesWithArchive().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getTrashedNotes(): Flow<List<Note>> {
        return noteDao.getTrashedNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getArchivedNotes(): Flow<List<Note>> {
        return noteDao.getArchivedNotes().map { entities ->
            entities.map { it.toNote() }
        }
    }

    fun getNotesByFolder(folder: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(folder).map { entities ->
            entities.map { it.toNote() }
        }
    }

    override fun getLabels(): Flow<List<String>> = labelDao.getAllLabels()

    override suspend fun createLabel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext false
            if (name.isBlank()) return@withContext false

            val existing = root.findFile(name)
            if (existing != null && existing.isDirectory) {
                labelDao.insert(LabelEntity(name))
                return@withContext true
            }

            val newDir = root.createDirectory(name)
            if (newDir != null) {
                labelDao.insert(LabelEntity(name))
                return@withContext true
            }
            return@withContext false
        }

    override suspend fun deleteLabel(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext false
            val count = noteDao.countNotesInFolder(name)
            if (count > 0) return@withContext false

            root.findFile(name)?.delete()
            root.findFile("Trash")?.findFile(name)?.delete()

            labelDao.delete(name)
            return@withContext true
        }

    override suspend fun getNote(id: String): Note? {
        return noteDao.getNoteByPath(id)?.toNote()
    }

    override suspend fun saveNote(
        note: Note,
        oldFile: java.io.File?,
    ): String =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext ""
            val folderName = if (note.folder.isNullOrEmpty() || note.folder == "Unknown") "Inbox" else note.folder

            // Determine the actual root for searching/saving based on status
            val effectiveRoot =
                when {
                    note.isTrashed -> root.findFile("Trash") ?: root.createDirectory("Trash")
                    else -> root
                } ?: root

            var targetDir = effectiveRoot.findFile(folderName) ?: effectiveRoot.createDirectory(folderName) ?: return@withContext ""

            if (note.isArchived && !note.isTrashed) {
                targetDir = targetDir.findFile("Archived") ?: targetDir.createDirectory("Archived") ?: targetDir
            } else if (note.isPinned && !note.isArchived && !note.isTrashed) {
                targetDir = targetDir.findFile("Pinned") ?: targetDir.createDirectory("Pinned") ?: targetDir
            }

            var baseTitle = note.title.trim().ifEmpty { "Untitled" }
            var finalFileName = "$baseTitle.md"

            var conflict = false
            var targetFileDoc = targetDir.findFile(finalFileName)

            if (targetFileDoc != null) {
                if (oldFile != null && oldFile.name == finalFileName) {
                    conflict = false
                } else {
                    conflict = true
                }
            }

            var counter = 1
            var finalTitle = baseTitle
            while (conflict) {
                finalTitle = "$baseTitle ($counter)"
                finalFileName = "$finalTitle.md"
                targetFileDoc = targetDir.findFile(finalFileName)
                if (targetFileDoc == null) conflict = false else counter++
            }

            val filePath = "$folderName/$finalFileName"

            if (oldFile != null) {
                val oldName = oldFile.name
                val oldParentName = oldFile.parent ?: "Inbox"

                val folderDoc = root.findFile(oldParentName)
                val trashDoc = root.findFile("Trash")?.findFile(oldParentName)
                
                var oldFileDoc: DocumentFile? = folderDoc?.findFile(oldName)
                    ?: folderDoc?.findFile("Pinned")?.findFile(oldName)
                    ?: folderDoc?.findFile("Archived")?.findFile(oldName)
                    ?: trashDoc?.findFile(oldName)

                if (oldFileDoc != null && oldFileDoc.uri != targetFileDoc?.uri) {
                    if (oldName != finalFileName || oldFileDoc.parentFile?.name != targetDir.name) {
                        oldFileDoc.delete()
                        noteDao.deleteNoteByPath("$oldParentName/$oldName")
                    }
                }
            }

            if (targetFileDoc == null) {
                targetFileDoc = targetDir.createFile("text/markdown", finalFileName)
            }

            val existingContent = targetFileDoc?.let { readText(it) }
            val fullContent = NoteFormatUtils.constructFileContent(note, existingContent)

            targetFileDoc?.let { doc ->
                context.contentResolver.openOutputStream(doc.uri, "wt")?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(fullContent)
                    }
                }
            }

            val entity =
                NoteEntity(
                    filePath = filePath,
                    fileName = finalFileName,
                    folder = folderName,
                    title = finalTitle,
                    contentPreview = note.content.take(200),
                    content = note.content,
                    lastModifiedMs = System.currentTimeMillis(),
                    color = note.color,
                    reminder = note.reminder,
                    isPinned = note.isPinned,
                    isArchived = note.isArchived,
                    isTrashed = note.isTrashed,
                )
            noteDao.insertNote(entity)

            return@withContext filePath
        }

    override suspend fun deleteNote(id: String) =
        withContext(Dispatchers.IO) {
            moveNoteToSystemFolder(id, isArchive = false)
            noteDao.trashNote(id)
        }

    override suspend fun deleteNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNotesByPaths(noteIds)
            moveNoteEntitiesToSystemFolder(entities, isArchive = false)
            noteDao.trashNotes(entities.map { it.filePath })
        }

    override suspend fun archiveNote(id: String) =
        withContext(Dispatchers.IO) {
            moveNoteToSystemFolder(id, isArchive = true)
            noteDao.archiveNote(id)
        }

    override suspend fun archiveNotes(noteIds: List<String>) =
        withContext(Dispatchers.IO) {
            val entities = noteDao.getNotesByPaths(noteIds)
            moveNoteEntitiesToSystemFolder(entities, isArchive = true)
            noteDao.archiveNotes(entities.map { it.filePath })
        }

    override suspend fun restoreNote(id: String) =
        withContext(Dispatchers.IO) {
            val entity = noteDao.getNoteByPath(id) ?: return@withContext
            val root = rootDir ?: return@withContext

            val folder = entity.folder
            val fileName = entity.fileName

            val deletedSource = root.findFile("Trash")?.findFile(folder)?.findFile(fileName)
            val archiveSource = root.findFile(folder)?.findFile("Archived")?.findFile(fileName)
            val sourceFile = deletedSource ?: archiveSource

            if (sourceFile != null) {
                val targetFolder = root.findFile(folder) ?: root.createDirectory(folder)
                val content = readText(sourceFile)
                val newFile = targetFolder?.createFile("text/markdown", fileName)
                newFile?.let { nf ->
                    context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                        OutputStreamWriter(os).use { it.write(content) }
                    }
                    sourceFile.delete()
                }
            }

            noteDao.restoreNote(id)
        }

    override suspend fun setNoteColor(
        id: String,
        color: Long,
    ) = withContext(Dispatchers.IO) {
        val entity = noteDao.getNoteByPath(id) ?: return@withContext
        val note = entity.toNote().copy(color = color)
        saveNote(note, note.file)
    }

    override suspend fun togglePinStatus(
        noteIds: List<String>,
        isPinned: Boolean,
    ) = withContext(Dispatchers.IO) {
        val root = rootDir ?: return@withContext
        val entities = noteDao.getNotesByPaths(noteIds).filter { it.isPinned != isPinned }

        entities.forEach { entity ->
            val folderName = entity.folder
            val fileName = entity.fileName

            var sourceFile = root.findFile(folderName)?.findFile(fileName)
            if (sourceFile == null) sourceFile = root.findFile(folderName)?.findFile("Pinned")?.findFile(fileName)

            if (sourceFile != null) {
                val targetDirParent = root.findFile(folderName) ?: root.createDirectory(folderName)
                val targetDir =
                    if (isPinned) {
                        targetDirParent?.findFile("Pinned") ?: targetDirParent?.createDirectory("Pinned")
                    } else {
                        targetDirParent
                    }

                if (targetDir != null) {
                    val content = readText(sourceFile)
                    val newFile = targetDir.createFile("text/markdown", fileName)
                    newFile?.let { nf ->
                        context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                            OutputStreamWriter(os).use { it.write(content) }
                        }
                        sourceFile.delete()
                    }
                }
            }
        }

        noteDao.updatePinStatuses(entities.map { it.filePath }, isPinned)
    }

    override suspend fun moveNotes(
        notes: List<Note>,
        targetFolder: String,
    ) = withContext(Dispatchers.IO) {
        val root = rootDir ?: return@withContext

        notes.forEach {
            val fileName = it.file.name
            val sourceFolder = it.folder
            val isArchived = it.isArchived
            val isTrashed = it.isTrashed
            val isPinned = it.isPinned

            val effectiveRoot =
                when {
                    isTrashed -> root.findFile("Trash")
                    else -> root
                }

            var sourceFile = effectiveRoot?.findFile(sourceFolder)?.findFile(fileName)
            if (sourceFile == null && !isTrashed && !isArchived) {
                sourceFile = effectiveRoot?.findFile(sourceFolder)?.findFile("Pinned")?.findFile(fileName)
            } else if (isArchived && !isTrashed) {
                sourceFile = effectiveRoot?.findFile(sourceFolder)?.findFile("Archived")?.findFile(fileName)
            }

            var targetRoot =
                when {
                    isTrashed -> root.findFile("Trash")
                    else -> root
                }

            var targetFolderDoc = targetRoot?.findFile(targetFolder) ?: targetRoot?.createDirectory(targetFolder)

            if (isPinned && !isTrashed && !isArchived) {
                targetFolderDoc = targetFolderDoc?.findFile("Pinned") ?: targetFolderDoc?.createDirectory("Pinned")
            } else if (isArchived && !isTrashed) {
                targetFolderDoc = targetFolderDoc?.findFile("Archived") ?: targetFolderDoc?.createDirectory("Archived")
            }

            if (sourceFile != null && targetFolderDoc != null) {
                val content = readText(sourceFile)
                val newFile = targetFolderDoc.createFile("text/markdown", fileName)
                newFile?.let { nf ->
                    context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                        OutputStreamWriter(os).use { it.write(content) }
                    }
                    sourceFile.delete()
                }

                val oldPath = "$sourceFolder/$fileName"
                val newPath = "$targetFolder/$fileName"
                val entity = noteDao.getNoteByPath(oldPath)
                if (entity != null) {
                    noteDao.deleteNoteByPath(oldPath)
                    noteDao.insertNote(entity.copy(filePath = newPath, folder = targetFolder))
                }
            }
        }
    }

    override suspend fun refreshNotes() =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext

            try {
                // 1. Get current DB state
                val dbNotes = noteDao.getAllNotesSync().associateBy { it.filePath }
                val dbPaths = dbNotes.keys

                // 2. Scan file system for file metadata only
                val fsFiles = mutableMapOf<String, FileMeta>()

                try {
                    if (root.findFile("Inbox") == null) root.createDirectory("Inbox")

                    // Scan standard folders
                    root.listFiles().filter { it.isDirectory && !it.name!!.startsWith(".") && it.name != "Trash" }.forEach { folder ->
                        scanFolderMeta(folder, isArchived = false, isTrashed = false, fsFiles)
                    }

                    // Scan Trash
                    root.findFile("Trash")?.listFiles()?.filter { it.isDirectory }?.forEach { folder ->
                        scanFolderMeta(folder, isArchived = false, isTrashed = true, fsFiles)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("RoomNoteRepository", "Error scanning root structure", e)
                }

                val fsPaths = fsFiles.keys

                // 3. Determine changes
                val toDelete = dbPaths.filter { !fsPaths.contains(it) }
                val toProcess =
                    fsPaths.filter { path ->
                        val meta = fsFiles[path]!!
                        val dbNote = dbNotes[path]
                        dbNote == null || meta.lastModified > dbNote.lastModifiedMs
                    }

                // 4. Process updates (Read content only for changed files)
                val notesToUpsert = mutableListOf<NoteEntity>()
                toProcess.forEach { path ->
                    try {
                        val meta = fsFiles[path]!!
                        val rawContent = readText(meta.file)
                        val frontMatter = NoteFormatUtils.parseFrontMatter(rawContent)

                        val color = if (frontMatter.color != 0xFFFFFFFF) frontMatter.color else appConfig.fileColors[meta.fileName] ?: 0xFFFFFFFF

                        notesToUpsert.add(
                            NoteEntity(
                                filePath = path,
                                fileName = meta.fileName,
                                folder = meta.folderName,
                                title = meta.fileName.substringBeforeLast("."),
                                contentPreview = frontMatter.cleanContent.take(200),
                                content = frontMatter.cleanContent,
                                lastModifiedMs = meta.lastModified,
                                color = color,
                                reminder = frontMatter.reminder,
                                isPinned = meta.isPinned,
                                isArchived = meta.isArchived,
                                isTrashed = meta.isTrashed,
                            ),
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("RoomNoteRepository", "Error processing file: $path", e)
                    }
                }

                // 5. Update DB
                if (toDelete.isNotEmpty()) {
                    noteDao.deleteNotesByPaths(toDelete)
                }
                if (notesToUpsert.isNotEmpty()) {
                    noteDao.insertNotes(notesToUpsert)
                }

                // 6. Sync Labels (Simple approach: rebuild from current valid notes)
                // Ideally we'd do this incrementally too, but labels are lightweight.
                val currentLabels = mutableSetOf<String>()
                try {
                    // Add folders from FS
                    root.listFiles().filter { it.isDirectory && !it.name!!.startsWith(".") && it.name != "Trash" }.forEach {
                        it.name?.let { name -> currentLabels.add(name) }
                    }

                    // Update labels in DB
                    // We can just nuke and rebuild labels as they are just folder names
                    labelDao.deleteAll()
                    labelDao.insertAll(currentLabels.map { LabelEntity(it) })
                } catch (e: Exception) {
                    android.util.Log.e("RoomNoteRepository", "Error syncing labels", e)
                }
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Critical error in refreshNotes", e)
            }
        }

    private data class FileMeta(
        val file: DocumentFile,
        val fileName: String,
        val folderName: String,
        val lastModified: Long,
        val isPinned: Boolean,
        val isArchived: Boolean,
        val isTrashed: Boolean,
    )

    private fun scanFolderMeta(
        folder: DocumentFile,
        isArchived: Boolean,
        isTrashed: Boolean,
        output: MutableMap<String, FileMeta>,
    ) {
        val folderName = folder.name ?: return

        fun processFiles(
            dir: DocumentFile,
            isPinned: Boolean,
            isArchiveTarget: Boolean
        ) {
            try {
                dir.listFiles().filter { it.isFile && (it.name?.endsWith(".md") == true || it.name?.endsWith(".txt") == true) }.forEach {
                        file ->
                    val fileName = file.name ?: return@forEach
                    val filePath = "$folderName/$fileName"
                    output[filePath] =
                        FileMeta(
                            file = file,
                            fileName = fileName,
                            folderName = folderName,
                            lastModified = file.lastModified(),
                            isPinned = isPinned,
                            isArchived = isArchiveTarget,
                            isTrashed = isTrashed,
                        )
                }
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Error scanning folder: ${dir.uri}", e)
            }
        }

        processFiles(folder, isPinned = false, isArchiveTarget = isArchived)

        if (!isArchived && !isTrashed) {
            try {
                folder.findFile("Pinned")?.let {
                    processFiles(it, isPinned = true, isArchiveTarget = false)
                }
                folder.findFile("Archived")?.let {
                    processFiles(it, isPinned = false, isArchiveTarget = true)
                }
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Error scanning subfolders in $folderName", e)
            }
        }
    }

    private suspend fun moveNoteToSystemFolder(
        id: String,
        isArchive: Boolean
    ) {
        val entity = noteDao.getNoteByPath(id) ?: return
        moveNoteEntitiesToSystemFolder(listOf(entity), isArchive)
    }

    private suspend fun moveNoteEntitiesToSystemFolder(
        entities: List<NoteEntity>,
        isArchive: Boolean
    ) {
        val root = rootDir ?: return
        entities.forEach { entity ->
            val folder = entity.folder
            val fileName = entity.fileName

            var sourceFile =
                root.findFile(folder)?.findFile(fileName)
                    ?: root.findFile(folder)?.findFile("Pinned")?.findFile(fileName)
                    ?: root.findFile(folder)?.findFile("Archived")?.findFile(fileName)
                    ?: root.findFile("Trash")?.findFile(folder)?.findFile(fileName)

            if (sourceFile != null) {
                val targetLabelFolder = if (isArchive) {
                    val base = root.findFile(folder) ?: root.createDirectory(folder)
                    base?.findFile("Archived") ?: base?.createDirectory("Archived")
                } else {
                    val sysRoot = root.findFile("Trash") ?: root.createDirectory("Trash")
                    sysRoot?.findFile(folder) ?: sysRoot?.createDirectory(folder)
                }

                if (targetLabelFolder != null) {
                    val content = readText(sourceFile)
                    val newFile = targetLabelFolder.createFile("text/markdown", fileName)
                    newFile?.let { nf ->
                        context.contentResolver.openOutputStream(nf.uri)?.use { os ->
                            OutputStreamWriter(os).use { it.write(content) }
                        }
                        sourceFile.delete()
                    }
                }
            }
        }
    }

    private suspend fun readText(file: DocumentFile): String =
        withContext(Dispatchers.IO) {
            val pathKey = file.uri.toString()
            val lastModified = file.lastModified()

            cacheMutex.withLock {
                val cached = contentCache[pathKey]
                if (cached != null && cached.first == lastModified) {
                    return@withContext cached.second
                }
            }

            try {
                context.contentResolver.openInputStream(file.uri)?.use { inputStream ->
                    val text = BufferedReader(inputStream.reader()).use { it.readText() }
                    cacheMutex.withLock {
                        if (contentCache.size >= 200) {
                            contentCache.remove(contentCache.keys.first())
                        }
                        contentCache[pathKey] = Pair(lastModified, text)
                    }
                    text
                } ?: ""
            } catch (e: Exception) {
                android.util.Log.e("RoomNoteRepository", "Exception reading markdown.", e)
                ""
            }
        }

    override suspend fun emptyTrash() =
        withContext(Dispatchers.IO) {
            val root = rootDir ?: return@withContext
            val deletedDir = root.findFile("Trash")

            deletedDir?.listFiles()?.forEach {
                it.delete()
            }

            noteDao.deleteAllTrashed()
        }

    private fun NoteEntity.toNote(): Note {
        return Note(
            file = java.io.File(folder, fileName),
            title = title,
            content = content,
            lastModified = Date(lastModifiedMs),
            color = color,
            reminder = reminder,
            isPinned = isPinned,
            isArchived = isArchived,
            isTrashed = isTrashed,
        )
    }
}
