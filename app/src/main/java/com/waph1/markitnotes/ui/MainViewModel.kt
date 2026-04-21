package com.waph1.markitnotes.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.waph1.markitnotes.data.model.Note
import com.waph1.markitnotes.data.receiver.NotificationScheduler
import com.waph1.markitnotes.data.repository.MetadataManager
import com.waph1.markitnotes.data.repository.PrefsManager
import com.waph1.markitnotes.data.repository.RoomNoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: RoomNoteRepository,
    private val metadataManager: MetadataManager,
    private val prefsManager: PrefsManager,
    private val notificationScheduler: NotificationScheduler,
) : ViewModel() {
    sealed interface NoteFilter {
        object All : NoteFilter

        data class Label(val name: String) : NoteFilter

        object Archive : NoteFilter

        object Trash : NoteFilter
    }

    sealed interface Screen {
        object Dashboard : Screen

        object Reminders : Screen
    }

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Dashboard)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
    }

    private val _currentFilter = MutableStateFlow<NoteFilter>(NoteFilter.All)
    val currentFilter: StateFlow<NoteFilter> = _currentFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(prefsManager.getSortOrder())
    val sortOrder: StateFlow<PrefsManager.SortOrder> = _sortOrder.asStateFlow()

    private val _sortDirection = MutableStateFlow(prefsManager.getSortDirection())
    val sortDirection: StateFlow<PrefsManager.SortDirection> = _sortDirection.asStateFlow()

    private val _viewMode = MutableStateFlow(prefsManager.getViewMode())
    val viewMode: StateFlow<PrefsManager.ViewMode> = _viewMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchEverywhere = MutableStateFlow(false)
    val isSearchEverywhere: StateFlow<Boolean> = _isSearchEverywhere.asStateFlow()

    val allNotes = repository.getAllNotesWithArchive()

    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val _notesRaw: StateFlow<List<Note>> =
        combine(
            _currentFilter.flatMapLatest { filter ->
                when (filter) {
                    is NoteFilter.All -> repository.getAllNotes()
                    is NoteFilter.Label -> repository.getNotesByFolder(filter.name)
                    is NoteFilter.Archive -> repository.getArchivedNotes()
                    is NoteFilter.Trash -> repository.getTrashedNotes()
                }
            },
            repository.getAllNotesWithArchive(),
            _sortOrder,
            _sortDirection,
            _searchQuery
                .debounce(300L)
                .distinctUntilChanged(),
        ) { notesList, allNotesList, order, direction, query ->
            val currentFilterValue = _currentFilter.value
            val searched =
                if (query.isBlank()) {
                    _isSearchEverywhere.value = false
                    notesList
                } else {
                    val q = query.lowercase()
                    val filteredResults =
                        notesList.filter {
                            it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
                        }

                    if (filteredResults.isEmpty() && currentFilterValue !is NoteFilter.Trash) {
                        val globalResults =
                            allNotesList.filter {
                                it.title.lowercase().contains(q) || it.content.lowercase().contains(q)
                            }
                        _isSearchEverywhere.value = globalResults.isNotEmpty()
                        globalResults
                    } else {
                        _isSearchEverywhere.value = false
                        filteredResults
                    }
                }

            val sorted =
                when (order) {
                    PrefsManager.SortOrder.DATE_MODIFIED -> searched.sortedBy { it.lastModified }
                    PrefsManager.SortOrder.TITLE -> searched.sortedBy { it.title.lowercase() }
                }

            val directed =
                if (direction == PrefsManager.SortDirection.DESCENDING) {
                    sorted.reversed()
                } else {
                    sorted
                }

            directed.sortedByDescending { it.isPinned }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Public notes StateFlow; the loading flag is driven by setRootFolder/refreshNotes, not the Flow mapper.
    val notes: StateFlow<List<Note>> = _notesRaw

    val uiItems: StateFlow<List<DashboardUiItem>> =
        combine(
            notes,
            _currentFilter,
            _isSearchEverywhere,
            _searchQuery,
        ) { notesList, filter, isGlobalSearch, query ->
            val list = mutableListOf<DashboardUiItem>()
            val usedKeys = mutableSetOf<String>()

            fun addUnique(item: DashboardUiItem) {
                if (usedKeys.add(item.key)) {
                    list.add(item)
                }
            }

            if (isGlobalSearch) {
                addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.SEARCH_EVERYWHERE))
                notesList.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
            } else if (query.isNotBlank()) {
                addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.SEARCH_RESULTS))
                notesList.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
            } else if (filter is NoteFilter.Trash || filter is NoteFilter.Archive) {
                notesList.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
            } else {
                val pinned = notesList.filter { it.isPinned && !it.isArchived }
                val others = notesList.filter { !it.isPinned && !it.isArchived }
                val archived = notesList.filter { it.isArchived }
                val showSeparator = filter is NoteFilter.Label

                if (pinned.isNotEmpty()) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.PINNED))
                    pinned.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
                }

                if (pinned.isNotEmpty() && others.isNotEmpty()) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.OTHERS))
                }
                others.forEach { addUnique(DashboardUiItem.NoteItem(it)) }

                if (archived.isNotEmpty() && showSeparator) {
                    addUnique(DashboardUiItem.HeaderItem(DashboardUiItem.HeaderType.ARCHIVED))
                    archived.forEach { addUnique(DashboardUiItem.NoteItem(it)) }
                }
            }
            addUnique(DashboardUiItem.SpacerItem)
            list
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tempLabels = MutableStateFlow<Set<String>>(emptySet())

    val labels: StateFlow<List<String>> =
        combine(
            repository.getLabels(),
            _tempLabels,
        ) { dbLabels, tempLabels ->
            (dbLabels + tempLabels).distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPermissionNeeded = MutableStateFlow(prefsManager.getRootUri() == null)
    val isPermissionNeeded: StateFlow<Boolean> = _isPermissionNeeded.asStateFlow()

    private val _currentNote = MutableStateFlow<Note?>(null)
    val currentNote: StateFlow<Note?> = _currentNote.asStateFlow()

    private val _isEditorOpen = MutableStateFlow(false)
    val isEditorOpen: StateFlow<Boolean> = _isEditorOpen.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setRootFolder(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.setRootFolder(uri.toString())
                _isPermissionNeeded.value = false
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to set root folder", e)
                _isPermissionNeeded.value = true
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshNotes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.refreshNotes()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to refresh notes", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetPermissionNeeded() {
        _isPermissionNeeded.value = true
    }

    fun openNote(note: Note) {
        viewModelScope.launch {
            try {
                val fullNote = repository.getNote(note.file.path)
                _currentNote.value = fullNote ?: note
                _isEditorOpen.value = true
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to load note content", e)
                _currentNote.value = note
                _isEditorOpen.value = true
            }
        }
    }

    fun createNote() {
        _currentNote.value = null
        _isEditorOpen.value = true
    }

    fun closeEditor() {
        _isEditorOpen.value = false
        _currentNote.value = null
    }

    fun setFilter(filter: NoteFilter) {
        _currentFilter.value = filter
    }

    fun createLabel(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val success = repository.createLabel(name)
            if (success) {
                val current = _tempLabels.value.toMutableSet()
                current.add(name)
                _tempLabels.value = current
            }
        }
    }

    fun deleteLabel(
        name: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val success = repository.deleteLabel(name)
            if (success) {
                val current = _tempLabels.value.toMutableSet()
                if (current.remove(name)) {
                    _tempLabels.value = current
                }
                onSuccess()
            } else {
                onError("Label must be empty to delete it")
            }
        }
    }

    fun deleteNote(note: Note) {
        notificationScheduler.cancel(note)
        viewModelScope.launch {
            // Remove reminder from metadata before moving to trash
            repository.saveNote(note.copy(reminder = null), note.file)
            repository.deleteNote(note.file.path)
        }
    }

    fun archiveNote(note: Note) {
        viewModelScope.launch {
            repository.archiveNote(note.file.path)
        }
    }

    fun restoreNote(note: Note) {
        viewModelScope.launch {
            repository.restoreNote(note.file.path)
        }
    }

    fun saveNote(
        note: Note,
        oldFile: java.io.File? = null,
    ) {
        viewModelScope.launch {
            try {
                val savedPath = repository.saveNote(note, oldFile)
                if (savedPath.isNotEmpty()) {
                    val updatedFile = java.io.File(savedPath)
                    val newTitle = updatedFile.nameWithoutExtension
                    val finalNote = note.copy(file = updatedFile, title = newTitle)

                    val current = _currentNote.value
                    val editorOpen = _isEditorOpen.value
                    if (current != null && current.file.path == oldFile?.path) {
                        _currentNote.value = finalNote
                    } else if (current == null && oldFile == null && editorOpen) {
                        _currentNote.value = finalNote
                    }

                    if (finalNote.reminder != null) {
                        notificationScheduler.schedule(finalNote)
                    } else {
                        notificationScheduler.cancel(finalNote)
                    }

                    if (oldFile != null && oldFile.path != updatedFile.path) {
                        val oldNote = note.copy(file = oldFile)
                        notificationScheduler.cancel(oldNote)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Failed to save note", e)
            }
        }
    }

    fun setSortOrder(order: PrefsManager.SortOrder) {
        _sortOrder.value = order
        prefsManager.saveSortOrder(order)
    }

    fun setSortDirection(direction: PrefsManager.SortDirection) {
        _sortDirection.value = direction
        prefsManager.saveSortDirection(direction)
    }

    fun setViewMode(mode: PrefsManager.ViewMode) {
        _viewMode.value = mode
        prefsManager.saveViewMode(mode)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    private val _selectedNotes = MutableStateFlow<Set<String>>(emptySet())
    val selectedNotes: StateFlow<Set<String>> = _selectedNotes.asStateFlow()

    fun toggleSelection(note: Note) {
        val current = _selectedNotes.value.toMutableSet()
        if (current.contains(note.file.path)) {
            current.remove(note.file.path)
        } else {
            current.add(note.file.path)
        }
        _selectedNotes.value = current
    }

    fun clearSelection() {
        _selectedNotes.value = emptySet()
    }

    fun deleteSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()
        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToDelete = allNotesList.filter { selectedIds.contains(it.file.path) }

            notesToDelete.forEach { note ->
                notificationScheduler.cancel(note)
                repository.saveNote(note.copy(reminder = null), note.file)
            }
            repository.deleteNotes(selectedIds)
        }
    }

    fun archiveSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()
        viewModelScope.launch {
            repository.archiveNotes(selectedIds)
        }
    }

    fun restoreSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()
        viewModelScope.launch {
            selectedIds.forEach { repository.restoreNote(it) }
        }
    }

    fun moveSelectedNotes(targetLabel: String) {
        val selectedIds = _selectedNotes.value.toSet()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToMove = allNotesList.filter { selectedIds.contains(it.file.path) }

            val targetFolder = if (targetLabel.isEmpty()) "Inbox" else targetLabel

            if (targetFolder != "Inbox") {
                val current = _tempLabels.value.toMutableSet()
                current.add(targetFolder)
                _tempLabels.value = current
            }

            repository.moveNotes(notesToMove, targetFolder)
        }
    }

    fun updateSelectedNotesColor(color: Long) {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToUpdate = allNotesList.filter { selectedIds.contains(it.file.path) }

            notesToUpdate.forEach { note ->
                repository.setNoteColor(note.file.path, color)
            }
        }
    }

    fun updateSelectedNotesReminder(timestamp: Long?) {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToUpdate = allNotesList.filter { selectedIds.contains(it.file.path) }

            notesToUpdate.forEach { note ->
                val updatedNote = note.copy(reminder = timestamp)
                repository.saveNote(updatedNote, note.file)
                if (timestamp != null) {
                    notificationScheduler.schedule(updatedNote)
                } else {
                    notificationScheduler.cancel(updatedNote)
                }
            }
        }
    }

    fun togglePinSelectedNotes() {
        val selectedIds = _selectedNotes.value.toList()
        clearSelection()

        viewModelScope.launch {
            val allNotesList = allNotes.first()
            val notesToUpdate = allNotesList.filter { selectedIds.contains(it.file.path) }
            val shouldPin = notesToUpdate.any { !it.isPinned }
            repository.togglePinStatus(selectedIds, shouldPin)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }
}
