package com.waph1.markitnotes.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.waph1.markitnotes.R
import com.waph1.markitnotes.data.model.Note
import com.waph1.markitnotes.data.repository.PrefsManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    listState: LazyStaggeredGridState,
    isDrawerOpen: Boolean,
    onSelectFolder: () -> Unit,
    onNoteClick: (Note) -> Unit,
    onFabClick: () -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val notes by viewModel.notes.collectAsState()
    val uiItems by viewModel.uiItems.collectAsState()
    val labels by viewModel.labels.collectAsState()
    val isPermissionNeeded by viewModel.isPermissionNeeded.collectAsState()
    val currentFilter by viewModel.currentFilter.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    // UI State
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }
    var labelToDelete by remember { mutableStateOf<String?>(null) }

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                viewModel.createLabel(name)
                showCreateLabelDialog = false
            },
        )
    }

    if (showEmptyTrashDialog) {
        AlertDialog(
            onDismissRequest = { showEmptyTrashDialog = false },
            title = { Text(stringResource(R.string.empty_trash_title)) },
            text = { Text(stringResource(R.string.empty_trash_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.emptyTrash()
                    showEmptyTrashDialog = false
                }) {
                    Text(stringResource(R.string.empty_trash_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyTrashDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (labelToDelete != null) {
        AlertDialog(
            onDismissRequest = { labelToDelete = null },
            title = { Text(stringResource(R.string.delete_label_title)) },
            text = { Text(stringResource(R.string.delete_label_message, labelToDelete!!)) },
            confirmButton = {
                TextButton(onClick = {
                    val name = labelToDelete!!
                    viewModel.deleteLabel(
                        name = name,
                        onSuccess = {
                            Toast.makeText(context, context.getString(R.string.label_deleted_toast), Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            val localizedError =
                                if (error == "Label must be empty to delete it") {
                                    context.getString(R.string.error_delete_label_not_empty)
                                } else {
                                    error
                                }
                            Toast.makeText(context, localizedError, Toast.LENGTH_SHORT).show()
                        },
                    )
                    labelToDelete = null
                }) {
                    Text(stringResource(R.string.delete_label_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { labelToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Selection State
    val selectedNotes by viewModel.selectedNotes.collectAsState()
    val isInSelectionMode = selectedNotes.isNotEmpty()

    val selectedNotesList = notes.filter { selectedNotes.contains(it.file.path) }
    val allSelectedArchived = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.isArchived }
    val allSelectedActive = selectedNotesList.isNotEmpty() && selectedNotesList.all { !it.isArchived && !it.isTrashed }

    val allHaveReminders = selectedNotesList.isNotEmpty() && selectedNotesList.all { it.reminder != null }
    val selectionInitialReminder = if (allHaveReminders) selectedNotesList.firstOrNull()?.reminder else null

    // Double back to exit
    var lastBackPressTime by remember { mutableStateOf(0L) }

    BackHandler(enabled = !isDrawerOpen) {
        when {
            isInSelectionMode -> viewModel.clearSelection()
            else -> {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    Toast.makeText(context, context.getString(R.string.press_back_again_exit), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            AnimatedVisibility(
                visible = isInSelectionMode,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
            ) {
                SelectionTopAppBar(
                    selectionCount = selectedNotes.size,
                    currentFilter = currentFilter,
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
            }
            AnimatedVisibility(
                visible = !isInSelectionMode,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                TopAppBar(
                    title = {
                        Surface(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 2.dp,
                        ) {
                            SearchBar(viewModel = viewModel)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Outlined.Menu, contentDescription = stringResource(R.string.menu))
                        }
                    },
                    actions = {
                        if (currentFilter is MainViewModel.NoteFilter.Trash) {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.empty_trash_desc)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            showEmptyTrashDialog = true
                                            showMoreMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            }
        },
        floatingActionButton = {
            if (!isPermissionNeeded &&
                currentFilter !is MainViewModel.NoteFilter.Trash &&
                currentFilter !is MainViewModel.NoteFilter.Archive
            ) {
                FloatingActionButton(onClick = onFabClick) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_note))
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isLoading && notes.isNotEmpty()) {
                LinearProgressIndicator(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
            ) {
                if (isPermissionNeeded) {
                    PermissionRequestState(
                        onSelectFolder = onSelectFolder,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    val pullRefreshState = rememberPullToRefreshState()
                    if (pullRefreshState.isRefreshing) {
                        LaunchedEffect(true) {
                            viewModel.refreshNotes()
                        }
                    }

                    LaunchedEffect(isLoading) {
                        if (!isLoading) {
                            pullRefreshState.endRefresh()
                        }
                    }

                    Box(modifier = Modifier.nestedScroll(pullRefreshState.nestedScrollConnection)) {
                        NoteGrid(
                            uiItems = uiItems,
                            selectedNotes = selectedNotes,
                            isLoading = isLoading,
                            notesCount = notes.size,
                            viewMode = viewMode,
                            listState = listState,
                            onNoteClick = { note ->
                                if (isInSelectionMode) {
                                    viewModel.toggleSelection(note)
                                } else {
                                    onNoteClick(note)
                                }
                            },
                            onNoteLongClick = { note -> viewModel.toggleSelection(note) },
                        )

                        PullToRefreshContainer(
                            state = pullRefreshState,
                            modifier = Modifier.align(Alignment.TopCenter),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteGrid(
    uiItems: List<DashboardUiItem>,
    selectedNotes: Set<String>,
    isLoading: Boolean,
    notesCount: Int,
    viewMode: PrefsManager.ViewMode,
    listState: LazyStaggeredGridState,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
) {
    if (isLoading && notesCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.loading_notes),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else if (notesCount == 0) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Text(
                    stringResource(R.string.no_results_found),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val columns = if (viewMode == PrefsManager.ViewMode.GRID) 2 else 1

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columns),
            state = listState,
            contentPadding = PaddingValues(0.dp),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                items = uiItems,
                key = { it.key },
                span = { item ->
                    if (item is DashboardUiItem.HeaderItem || item is DashboardUiItem.SpacerItem) {
                        StaggeredGridItemSpan.FullLine
                    } else {
                        StaggeredGridItemSpan.SingleLane
                    }
                },
            ) { item ->
                when (item) {
                    is DashboardUiItem.HeaderItem -> {
                        val title =
                            when (item.type) {
                                DashboardUiItem.HeaderType.PINNED -> stringResource(R.string.pinned)
                                DashboardUiItem.HeaderType.OTHERS -> stringResource(R.string.others)
                                DashboardUiItem.HeaderType.ARCHIVED -> stringResource(R.string.archived_notes_header)
                                DashboardUiItem.HeaderType.SEARCH_RESULTS -> stringResource(R.string.search_results)
                                DashboardUiItem.HeaderType.SEARCH_EVERYWHERE -> stringResource(R.string.search_everywhere)
                            }
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 8.dp, bottom = 4.dp, top = 8.dp),
                        ) {
                            if (item.type == DashboardUiItem.HeaderType.OTHERS ||
                                item.type == DashboardUiItem.HeaderType.ARCHIVED
                            ) {
                                HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                            }
                            Text(text = title, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    is DashboardUiItem.NoteItem -> {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically { it / 2 },
                        ) {
                            NoteCard(
                                note = item.note,
                                isSelected = selectedNotes.contains(item.note.file.path),
                                onClick = { onNoteClick(item.note) },
                                onLongClick = { onNoteLongClick(item.note) },
                            )
                        }
                    }
                    is DashboardUiItem.SpacerItem -> {
                        androidx.compose.foundation.layout.Spacer(
                            modifier =
                                Modifier
                                    .height(80.dp)
                                    .fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val noteColor = Color(note.color.toInt())

    val containerColor =
        if (isDark) {
            if (note.color == 0xFFFFFFFF.toLong()) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                noteColor.copy(alpha = 0.4f).compositeOver(MaterialTheme.colorScheme.surface)
            }
        } else {
            noteColor
        }

    val contentColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val titleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val contentStyle = MaterialTheme.typography.bodyMedium.copy(color = contentColor)

    val interactionSource = remember { MutableInteractionSource() }
    val onLongClickAction = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongClick()
    }

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material.ripple.rememberRipple(),
                    onClick = onClick,
                    onLongClick = onLongClickAction,
                )
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
                    } else {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    },
                ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (note.title.isNotEmpty()) {
                Text(
                    text = note.title,
                    style = titleStyle,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (note.content.isNotEmpty()) {
                Box {
                    val annotatedContent =
                        remember(note.content, isDark) {
                            try {
                                val previewContent =
                                    if (note.content.length > 500) {
                                        note.content.take(500) + "..."
                                    } else {
                                        note.content
                                    }
                                buildAnnotatedStringWithMarkdown(previewContent, -1, isDark)
                            } catch (e: Exception) {
                                androidx.compose.ui.text.AnnotatedString(note.content)
                            }
                        }

                    Text(
                        text = annotatedContent,
                        style = contentStyle,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    // Invisible overlay to capture clicks on the markdown text area
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onClick,
                                    onLongClick = onLongClickAction,
                                ),
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (note.folder != "Unknown" && note.folder != "Inbox") {
                    Text(
                        text = note.folder,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                if (note.reminder != null) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    val format = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
                    Text(
                        text = format.format(Date(note.reminder)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestState(
    onSelectFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        Text(
            text = stringResource(R.string.welcome_message),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp),
        )
        Button(onClick = onSelectFolder) {
            Text(stringResource(R.string.select_folder))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopAppBar(
    selectionCount: Int,
    currentFilter: MainViewModel.NoteFilter,
    allSelectedArchived: Boolean,
    allSelectedActive: Boolean,
    onClearSelection: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onRestore: () -> Unit,
    onMove: (String) -> Unit,
    onColorChange: (Long) -> Unit,
    onPin: () -> Unit,
    onReminderChange: (Long?) -> Unit,
    availableLabels: List<String>,
    initialReminder: Long? = null,
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    var showColorMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                onMove(name)
                showCreateLabelDialog = false
            },
        )
    }

    if (showDateTimePicker) {
        DateTimePickerDialog(
            onDismiss = { showDateTimePicker = false },
            onConfirm = { timestamp ->
                onReminderChange(timestamp)
                showDateTimePicker = false
            },
            onRemove = {
                onReminderChange(null)
                showDateTimePicker = false
            },
            initialTimestamp = initialReminder,
        )
    }

    val isTrash = currentFilter is MainViewModel.NoteFilter.Trash

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_selected_notes_title)) },
            text = { Text(stringResource(R.string.delete_selected_notes_message, selectionCount)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    TopAppBar(
        title = {
            Text(
                text = "$selectionCount",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_selection))
            }
        },
        actions = {
            if (isTrash || allSelectedArchived) {
                IconButton(onClick = onRestore) {
                    Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.restore))
                }
            }

            if (!isTrash) {
                IconButton(onClick = { showMoveMenu = true }) {
                    Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = stringResource(R.string.move))
                }
                DropdownMenu(
                    expanded = showMoveMenu,
                    onDismissRequest = { showMoveMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.inbox)) },
                        onClick = {
                            onMove("Inbox")
                            showMoveMenu = false
                        },
                    )
                    availableLabels.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onMove(label)
                                showMoveMenu = false
                            },
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.create_new_label)) },
                        leadingIcon = { Icon(Icons.Default.Add, null) },
                        onClick = {
                            showMoveMenu = false
                            showCreateLabelDialog = true
                        },
                    )
                }
            }

            if (!isTrash) {
                Box {
                    IconButton(onClick = { showColorMenu = true }) {
                        Box(
                            modifier =
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(Color.Gray),
                        )
                    }
                    DropdownMenu(
                        expanded = showColorMenu,
                        onDismissRequest = { showColorMenu = false },
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            NoteColors.palette.chunked(4).forEach { rowColors ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowColors.forEach { color ->
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(color.toInt()))
                                                    .border(1.dp, Color.Gray, CircleShape)
                                                    .clickable {
                                                        onColorChange(color)
                                                        showColorMenu = false
                                                    },
                                        )
                                    }
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                            }
                        }
                    }
                }
            }

            if (!isTrash) {
                IconButton(onClick = { showDateTimePicker = true }) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                }
            }

            if (!isTrash && allSelectedActive) {
                IconButton(onClick = onPin) {
                    Icon(Icons.Outlined.Star, contentDescription = stringResource(R.string.pin_unpin))
                }
            }

            Box {
                IconButton(onClick = { showMoreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false },
                ) {
                    if (!isTrash && allSelectedActive) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.archive)) },
                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                            onClick = {
                                onArchive()
                                showMoreMenu = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                        onClick = {
                            showDeleteDialog = true
                            showMoreMenu = false
                        },
                    )
                }
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    )
}

@Composable
fun CreateLabelDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.create_new_label)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.label_name_hint)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
