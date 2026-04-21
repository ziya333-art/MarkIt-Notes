package com.waph1.markitnotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.waph1.markitnotes.R
import com.waph1.markitnotes.data.model.Note
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val checkboxContinuationRegex = Regex("""^(\s*[-*+]\s+\[[ xX]\]\s*)(.*)$""")
private val bulletContinuationRegex = Regex("""^(\s*[-*+•]|\d+\.)\s+(.*)$""")
private val quoteContinuationRegex = Regex("""^(\s*>\s+)(.*)$""")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EditorScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    initialLabel: String = "",
) {
    val currentNote by viewModel.currentNote.collectAsState()
    val labels by viewModel.labels.collectAsState()

    var title by remember { mutableStateOf(currentNote?.title ?: "") }
    var content by remember { mutableStateOf(TextFieldValue(currentNote?.content ?: "")) }
    var color by remember { mutableStateOf(currentNote?.color ?: 0xFFFFFFFF) }
    var folder by remember(currentNote, initialLabel) {
        mutableStateOf(
            currentNote?.folder?.takeIf { it != "Unknown" && it != "Inbox" } ?: initialLabel,
        )
    }
    var reminder by remember { mutableStateOf(currentNote?.reminder) }

    // New notes start in Edit mode, existing notes in View mode
    var isEditing by remember { mutableStateOf(currentNote == null) }

    val focusRequester = remember { FocusRequester() }

    // UI state
    var showLabelMenu by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var showCreateLabelDialog by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showMathMenu by remember { mutableStateOf(false) }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Builds a [Note] from the current editor local-state variables. */
    fun buildCurrentNote(overrideReminder: Long? = reminder): Note {
        val parentPath = if (folder.isEmpty()) "Inbox" else folder
        val fileName = currentNote?.file?.name?.takeIf { it.isNotEmpty() } ?: "new_note_placeholder"
        val autoTitle =
            if (title.isNotEmpty()) {
                title
            } else {
                SimpleDateFormat("yyyy_MM_dd_HH_mm", Locale.getDefault()).format(Date())
            }
        return Note(
            file = File(parentPath, fileName),
            title = autoTitle,
            content = content.text,
            lastModified = Date(),
            color = color,
            reminder = overrideReminder,
            isPinned = currentNote?.isPinned ?: false,
            isArchived = currentNote?.isArchived ?: false,
            isTrashed = currentNote?.isTrashed ?: false,
        )
    }

    fun saveNote() {
        if (title.isNotEmpty() || content.text.isNotEmpty()) {
            val note = buildCurrentNote()
            val isChanged =
                if (currentNote == null) {
                    title.isNotEmpty() || content.text.isNotEmpty()
                } else {
                    title != currentNote?.title ||
                        content.text != currentNote?.content ||
                        color != currentNote?.color ||
                        reminder != currentNote?.reminder ||
                        folder != (currentNote?.folder?.takeIf { it != "Unknown" && it != "Inbox" } ?: "")
                }
            if (isChanged) {
                viewModel.saveNote(note, currentNote?.file)
            }
        }
    }

    fun insertAtCursor(
        prefix: String,
        suffix: String = "",
    ) {
        val text = content.text
        val start = content.selection.min
        val end = content.selection.max
        val selectedText = text.substring(start, end)
        val newText = text.substring(0, start) + prefix + selectedText + suffix + text.substring(end)
        val newCursor = start + prefix.length + selectedText.length
        content = TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    if (showDateTimePicker) {
        DateTimePickerDialog(
            onDismiss = { showDateTimePicker = false },
            onConfirm = { timestamp ->
                reminder = timestamp
                showDateTimePicker = false
                if (!isEditing) {
                    viewModel.saveNote(buildCurrentNote(overrideReminder = timestamp), currentNote?.file)
                }
            },
            onRemove = {
                reminder = null
                showDateTimePicker = false
                if (!isEditing && currentNote != null) {
                    viewModel.saveNote(buildCurrentNote(overrideReminder = null), currentNote!!.file)
                }
            },
            initialTimestamp = reminder,
        )
    }

    if (showCreateLabelDialog) {
        CreateLabelDialog(
            onDismiss = { showCreateLabelDialog = false },
            onConfirm = { name ->
                viewModel.createLabel(name)
                folder = name
                showCreateLabelDialog = false
            },
        )
    }

    LaunchedEffect(currentNote) {
        if (currentNote != null) {
            title = currentNote!!.title
            content = TextFieldValue(text = currentNote!!.content)
            color = currentNote!!.color
            folder = currentNote!!.folder.takeIf { it != "Unknown" && it != "Inbox" } ?: ""
            reminder = currentNote!!.reminder
        } else if (isEditing) {
            focusRequester.requestFocus()
        }
    }

    BackHandler {
        if (isEditing) {
            saveNote()
            isEditing = false
        } else {
            onBack()
        }
    }

    // ── theming ───────────────────────────────────────────────────────────────

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val noteColor = Color(color.toInt())
    val backgroundColor =
        if (isDark) {
            if (color == 0xFFFFFFFF.toLong()) {
                MaterialTheme.colorScheme.background
            } else {
                noteColor.copy(alpha = 0.1f).compositeOver(MaterialTheme.colorScheme.background)
            }
        } else {
            noteColor
        }

    // ── scaffold ──────────────────────────────────────────────────────────────

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    if (isEditing) {
                        IconButton(onClick = {
                            saveNote()
                            isEditing = false
                        }) {
                            Icon(Icons.Outlined.Check, contentDescription = stringResource(R.string.done))
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    // Label picker
                    Box {
                        IconButton(onClick = { showLabelMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Outlined.DriveFileMove,
                                contentDescription = stringResource(R.string.label),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showLabelMenu,
                            onDismissRequest = { showLabelMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.inbox_no_label)) },
                                onClick = {
                                    folder = ""
                                    showLabelMenu = false
                                },
                            )
                            labels.forEach { label ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        folder = label
                                        showLabelMenu = false
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.create_new_label)) },
                                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                                onClick = {
                                    showLabelMenu = false
                                    showCreateLabelDialog = true
                                },
                            )
                        }
                    }

                    // Reminder
                    IconButton(onClick = { showDateTimePicker = true }) {
                        Icon(
                            imageVector = if (reminder != null) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                            contentDescription = stringResource(R.string.reminders),
                            tint =
                                if (reminder != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }

                    // Edit toggle (view-mode only)
                    if (!isEditing) {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit))
                        }
                    }

                    // More menu (existing notes only)
                    val currentNoteObj = currentNote
                    if (currentNoteObj != null) {
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false },
                            ) {
                                if (!currentNoteObj.isTrashed) {
                                    if (currentNoteObj.isArchived) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.unarchive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                            onClick = {
                                                viewModel.restoreNote(currentNoteObj)
                                                showMoreMenu = false
                                                onBack()
                                            },
                                        )
                                    } else {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.archive)) },
                                            leadingIcon = { Icon(Icons.Outlined.Archive, null) },
                                            onClick = {
                                                viewModel.archiveNote(currentNoteObj)
                                                showMoreMenu = false
                                                onBack()
                                            },
                                        )
                                    }
                                }

                                if (currentNoteObj.isTrashed) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.restore)) },
                                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                                        onClick = {
                                            viewModel.restoreNote(currentNoteObj)
                                            showMoreMenu = false
                                            onBack()
                                        },
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.delete)) },
                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                        onClick = {
                                            viewModel.deleteNote(currentNoteObj)
                                            showMoreMenu = false
                                            onBack()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor),
            )
        },
        bottomBar = {
            if (isEditing) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(backgroundColor)
                            .navigationBarsPadding()
                            .imePadding(),
                ) {
                    if (showColorPicker) {
                        ColorPicker(
                            selectedColor = color,
                            onColorSelected = {
                                color = it
                                showColorPicker = false
                            },
                        )
                    } else {
                        LazyRow(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(color.toInt()))
                                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .clickable { showColorPicker = true },
                                )
                            }
                            item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                            item {
                                Box {
                                    ToolbarIconButton(
                                        text = "H1",
                                        bold = true,
                                        onClick = { insertAtCursor("# ") },
                                        onLongClick = { showHeadingMenu = true },
                                    )
                                    DropdownMenu(
                                        expanded = showHeadingMenu,
                                        onDismissRequest = { showHeadingMenu = false },
                                    ) {
                                        listOf("H1" to "# ", "H2" to "## ", "H3" to "### ", "H4" to "#### ")
                                            .forEach { (label, md) ->
                                                DropdownMenuItem(
                                                    text = { Text(label) },
                                                    onClick = {
                                                        insertAtCursor(md)
                                                        showHeadingMenu = false
                                                    },
                                                )
                                            }
                                    }
                                }
                            }
                            item { ToolbarIconButton(text = "—", onClick = { insertAtCursor("---\n") }) }
                            item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                            item { ToolbarIconButton(text = "B", bold = true, onClick = { insertAtCursor("**", "**") }) }
                            item { ToolbarIconButton(text = "I", italic = true, onClick = { insertAtCursor("_", "_") }) }
                            item { ToolbarIconButton(text = "U", underline = true, onClick = { insertAtCursor("<u>", "</u>") }) }
                            item { ToolbarIconButton(text = "S", strikethrough = true, onClick = { insertAtCursor("~~", "~~") }) }
                            item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                            item { ToolbarIconButton(text = "🔗", onClick = { insertAtCursor("[", "](url)") }) }
                            item { ToolbarIconButton(text = "<>", onClick = { insertAtCursor("`", "`") }) }
                            item { ToolbarIconButton(text = "\"", onClick = { insertAtCursor("> ") }) }
                            item { VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp)) }
                            item {
                                Box {
                                    ToolbarIconButton(
                                        text = "ƒ",
                                        onClick = { insertAtCursor("$", "$") },
                                        onLongClick = { showMathMenu = true },
                                    )
                                    DropdownMenu(
                                        expanded = showMathMenu,
                                        onDismissRequest = { showMathMenu = false },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.inline_math)) },
                                            onClick = {
                                                insertAtCursor("$", "$")
                                                showMathMenu = false
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.block_math)) },
                                            onClick = {
                                                insertAtCursor("$$\n", "\n$$")
                                                showMathMenu = false
                                            },
                                        )
                                    }
                                }
                            }
                            item { ToolbarIconButton(text = "•", onClick = { insertAtCursor("- ") }) }
                            item { ToolbarIconButton(text = "1.", onClick = { insertAtCursor("1. ") }) }
                            item { ToolbarIconButton(text = "☐", onClick = { insertAtCursor("- [ ] ") }) }
                        }
                    }
                }
            }
        },
        containerColor = backgroundColor,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
        ) {
            if (isEditing) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    placeholder = { Text(stringResource(R.string.title_hint)) },
                    textStyle = MaterialTheme.typography.headlineMedium,
                    colors =
                        TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                BasicTextField(
                    value = content,
                    onValueChange = { newValue ->
                        val oldText = content.text
                        val oldCursor = content.selection.start
                        val newText = newValue.text
                        val newCursor = newValue.selection.start

                        val isNewLineJustAdded =
                            newCursor > 0 &&
                                newText[newCursor - 1] == '\n' &&
                                newText.length == oldText.length + 1

                        if (isNewLineJustAdded) {
                            val committedValue = newValue.copy(composition = null)

                            // Syntax jump (e.g. Bold, Italic) – jump cursor past the closing marker
                            val syntaxSuffixes = listOf("**", "_", "~~", "</u>", "`", "$")
                            val textAfterOldCursor = oldText.substring(oldCursor)
                            val jumpSuffix = syntaxSuffixes.firstOrNull { textAfterOldCursor.startsWith(it) }

                            if (jumpSuffix != null) {
                                val textBefore = oldText.substring(0, oldCursor)
                                val textAfterSuffix = textAfterOldCursor.substring(jumpSuffix.length)
                                val finalJumpedText = textBefore + jumpSuffix + "\n" + textAfterSuffix
                                content =
                                    committedValue.copy(
                                        text = finalJumpedText,
                                        selection = TextRange(oldCursor + jumpSuffix.length + 1),
                                    )
                                return@BasicTextField
                            }

                            // Smart continuation (Lists, Checkboxes, Blockquotes)
                            val currentLineBreak = newCursor - 1
                            if (currentLineBreak != -1) {
                                val textBeforeLastNewline = newText.substring(0, currentLineBreak)
                                val lastLineStart = textBeforeLastNewline.lastIndexOf('\n') + 1
                                val lastLine = textBeforeLastNewline.substring(lastLineStart)
                                val trimmedLastLine = lastLine.trimEnd()

                                val checkboxMatch = checkboxContinuationRegex.find(trimmedLastLine)
                                val bulletMatch = bulletContinuationRegex.find(trimmedLastLine)
                                val quoteMatch = quoteContinuationRegex.find(trimmedLastLine)

                                when {
                                    checkboxMatch != null -> {
                                        val prefix = checkboxMatch.groups[1]!!.value
                                        val lineContent = checkboxMatch.groups[2]!!.value
                                        if (lineContent.trim().isEmpty()) {
                                            val clearedText = newText.substring(0, lastLineStart) + "\n" + newText.substring(newCursor)
                                            content = committedValue.copy(text = clearedText, selection = TextRange(lastLineStart + 1))
                                        } else {
                                            val continuedText = newText.substring(0, newCursor) + prefix + newText.substring(newCursor)
                                            content = committedValue.copy(text = continuedText, selection = TextRange(newCursor + prefix.length))
                                        }
                                        return@BasicTextField
                                    }
                                    bulletMatch != null -> {
                                        val prefixPart = bulletMatch.groups[1]!!.value
                                        val lineContent = bulletMatch.groups[2]!!.value
                                        if (lineContent.trim().isEmpty()) {
                                            val clearedText = newText.substring(0, lastLineStart) + "\n" + newText.substring(newCursor)
                                            content = committedValue.copy(text = clearedText, selection = TextRange(lastLineStart + 1))
                                        } else {
                                            val nextPrefix =
                                                if (prefixPart.trim().firstOrNull()?.isDigit() == true) {
                                                    val num = prefixPart.filter { it.isDigit() }.toInt()
                                                    "${num + 1}. "
                                                } else {
                                                    if (prefixPart.endsWith(" ")) prefixPart else "$prefixPart "
                                                }
                                            val continuedText = newText.substring(0, newCursor) + nextPrefix + newText.substring(newCursor)
                                            content = committedValue.copy(text = continuedText, selection = TextRange(newCursor + nextPrefix.length))
                                        }
                                        return@BasicTextField
                                    }
                                    quoteMatch != null -> {
                                        val prefix = quoteMatch.groups[1]!!.value
                                        val lineContent = quoteMatch.groups[2]!!.value
                                        if (lineContent.trim().isEmpty()) {
                                            val clearedText = newText.substring(0, lastLineStart) + "\n" + newText.substring(newCursor)
                                            content = committedValue.copy(text = clearedText, selection = TextRange(lastLineStart + 1))
                                        } else {
                                            val continuedText = newText.substring(0, newCursor) + prefix + newText.substring(newCursor)
                                            content = committedValue.copy(text = continuedText, selection = TextRange(newCursor + prefix.length))
                                        }
                                        return@BasicTextField
                                    }
                                }
                            }
                            content = committedValue
                        } else {
                            content = newValue
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    visualTransformation = MarkdownVisualTransformation(isDark, content.selection.start),
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onBackground,
                        ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .focusRequester(focusRequester),
                    decorationBox = { innerTextField ->
                        Box {
                            if (content.text.isEmpty()) {
                                Text(
                                    stringResource(R.string.start_typing_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            } else {
                PreviewWebView(
                    content = "# $title\n\n${content.text}",
                    isDark = isDark,
                    onCheckboxToggled = { index, checked ->
                        val newText = toggleTask(content.text, index, checked)
                        content = TextFieldValue(text = newText)
                        viewModel.saveNote(
                            buildCurrentNote().copy(content = newText),
                            currentNote?.file,
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolbarIconButton(
    text: String,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strikethrough: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                    textDecoration =
                        when {
                            underline && strikethrough ->
                                TextDecoration.combine(
                                    listOf(TextDecoration.Underline, TextDecoration.LineThrough),
                                )
                            underline -> TextDecoration.Underline
                            strikethrough -> TextDecoration.LineThrough
                            else -> null
                        },
                ),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
fun ColorPicker(
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(8.dp),
    ) {
        items(NoteColors.palette) { c ->
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(c.toInt()))
                        .border(
                            width = if (c == selectedColor) 2.dp else 1.dp,
                            color = if (c == selectedColor) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = CircleShape,
                        )
                        .clickable { onColorSelected(c) },
            )
        }
    }
}
