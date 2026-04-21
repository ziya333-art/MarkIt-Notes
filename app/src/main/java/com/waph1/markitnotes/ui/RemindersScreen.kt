package com.waph1.markitnotes.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.waph1.markitnotes.R
import com.waph1.markitnotes.data.model.Note
import dev.jeziellago.compose.markdowntext.MarkdownText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RemindersScreen(
    viewModel: MainViewModel,
    onOpenDrawer: () -> Unit,
    onNoteClick: (Note) -> Unit,
) {
    val notes by viewModel.allNotes.collectAsState(initial = emptyList())
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val labels by viewModel.labels.collectAsState()

    // Filter and Sort: Include Archived, Exclude Trash
    val reminderNotes =
        notes.filter { it.reminder != null && !it.isTrashed }
            .sortedBy { it.reminder }

    val now = Date()
    val cal = Calendar.getInstance()

    fun isToday(date: Date): Boolean {
        val noteCal = Calendar.getInstance().apply { time = date }
        return cal.get(Calendar.YEAR) == noteCal.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == noteCal.get(Calendar.DAY_OF_YEAR)
    }

    val overdue = reminderNotes.filter { Date(it.reminder ?: 0L) < now }
    val today = reminderNotes.filter { Date(it.reminder ?: 0L) >= now && isToday(Date(it.reminder ?: 0L)) }
    val future = reminderNotes.filter { Date(it.reminder ?: 0L) >= now && !isToday(Date(it.reminder ?: 0L)) }

    val isInSelectionMode = selectedNotes.isNotEmpty()

    // Calculate selection stats for TopBar
    val selectedNotesList = notes.filter { selectedNotes.contains(it.file.path) }
    val allSelectedArchived = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isArchived }
    val allSelectedActive = selectedNotesList.isNotEmpty() && selectedNotesList.all { !it.isArchived && !it.isTrashed }

    // Calculate if all selected have reminders
    val allHaveReminders = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.reminder != null }
    val selectionInitialReminder = if (allHaveReminders) selectedNotesList.firstOrNull()?.reminder else null

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                SelectionTopAppBar(
                    selectionCount = selectedNotes.size,
                    // Dummy filter
                    currentFilter = MainViewModel.NoteFilter.All,
                    allSelectedArchived = allSelectedArchived,
                    allSelectedActive = allSelectedActive,
                    onClearSelection = { viewModel.clearSelection() },
                    onDelete = { viewModel.deleteSelectedNotes() },
                    onArchive = { viewModel.archiveSelectedNotes() },
                    onRestore = { viewModel.restoreSelectedNotes() },
                    onMove = { targetLabel -> viewModel.moveSelectedNotes(targetLabel) },
                    onColorChange = { color -> viewModel.updateSelectedNotesColor(color) },
                    onPin = { viewModel.togglePinSelectedNotes() },
                    onReminderChange = { viewModel.updateSelectedNotesReminder(it) },
                    availableLabels = labels,
                    initialReminder = selectionInitialReminder,
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.reminders)) },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (reminderNotes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.reminders_empty), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            } else {
                if (overdue.isNotEmpty()) {
                    stickyHeader { SectionHeader(stringResource(R.string.reminders_section_overdue), true) }
                    items(overdue, key = { it.file.path }) { note ->
                        SwipeableReminderItem(
                            note = note,
                            isSelected = selectedNotes.contains(note.file.path),
                            onNoteClick = {
                                if (isInSelectionMode) viewModel.toggleSelection(it) else onNoteClick(it)
                            },
                            onNoteLongClick = { viewModel.toggleSelection(it) },
                            onDismiss = {
                                // Archive and Remove Reminder
                                viewModel.saveNote(note.copy(reminder = null, isArchived = true), note.file)
                            },
                        )
                    }
                }

                if (today.isNotEmpty()) {
                    stickyHeader { SectionHeader(stringResource(R.string.reminders_section_today), false) }
                    items(today, key = { it.file.path }) { note ->
                        SwipeableReminderItem(
                            note = note,
                            isSelected = selectedNotes.contains(note.file.path),
                            onNoteClick = {
                                if (isInSelectionMode) viewModel.toggleSelection(it) else onNoteClick(it)
                            },
                            onNoteLongClick = { viewModel.toggleSelection(it) },
                            onDismiss = {
                                viewModel.saveNote(note.copy(reminder = null, isArchived = true), note.file)
                            },
                        )
                    }
                }

                if (future.isNotEmpty()) {
                    stickyHeader { SectionHeader(stringResource(R.string.reminders_section_upcoming), false) }
                    items(future, key = { it.file.path }) { note ->
                        SwipeableReminderItem(
                            note = note,
                            isSelected = selectedNotes.contains(note.file.path),
                            onNoteClick = {
                                if (isInSelectionMode) viewModel.toggleSelection(it) else onNoteClick(it)
                            },
                            onNoteLongClick = { viewModel.toggleSelection(it) },
                            onDismiss = {
                                viewModel.saveNote(note.copy(reminder = null, isArchived = true), note.file)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    isUrgent: Boolean,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = if (isUrgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableReminderItem(
    note: Note,
    isSelected: Boolean,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it == SwipeToDismissBoxValue.EndToStart) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val color = MaterialTheme.colorScheme.secondaryContainer
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(color)
                        .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = "Archive",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        content = {
            ReminderNoteItem(note, isSelected, onNoteClick, onNoteLongClick)
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReminderNoteItem(
    note: Note,
    isSelected: Boolean,
    onClick: (Note) -> Unit,
    onLongClick: (Note) -> Unit,
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val noteColor = androidx.compose.ui.graphics.Color(note.color)
    val haptic = LocalHapticFeedback.current

    val containerColor =
        if (isDark) {
            if (note.color == 0xFFFFFFFF) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                noteColor.copy(alpha = 0.3f).compositeOver(MaterialTheme.colorScheme.surface)
            }
        } else {
            noteColor
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(containerColor)
                .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                .combinedClickable(
                    onClick = { onClick(note) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick(note)
                    },
                )
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (note.isArchived) {
                Icon(
                    Icons.Default.Archive,
                    contentDescription = "Archived",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (note.content.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            MarkdownText(
                markdown = note.content,
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
                maxLines = 2,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Due: ${dateFormat.format(Date(note.reminder!!))}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = note.folder,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
