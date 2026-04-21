package com.waph1.markitnotes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.waph1.markitnotes.data.receiver.NotificationScheduler
import com.waph1.markitnotes.data.repository.MetadataManager
import com.waph1.markitnotes.data.repository.PrefsManager
import com.waph1.markitnotes.data.repository.RoomNoteRepository
import com.waph1.markitnotes.ui.DashboardScreen
import com.waph1.markitnotes.ui.EditorScreen
import com.waph1.markitnotes.ui.MainViewModel
import com.waph1.markitnotes.ui.theme.KeepNotesTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var repository: RoomNoteRepository
    private lateinit var metadataManager: MetadataManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var notificationScheduler: NotificationScheduler

    private val viewModel by viewModels<MainViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, metadataManager, prefsManager, notificationScheduler) as T
            }
        }
    }

    private val openDocumentTreeLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                prefsManager.saveRootUri(it.toString())
                viewModel.setRootFolder(it)
            }
        }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* Permission granted or denied – handled reactively */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)

        metadataManager = MetadataManager(applicationContext)
        repository = RoomNoteRepository(applicationContext, metadataManager)
        prefsManager = PrefsManager(applicationContext)
        notificationScheduler = NotificationScheduler(applicationContext)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        handleIntent(intent)

        val savedUriStr = prefsManager.getRootUri()
        if (savedUriStr != null) {
            val uri = Uri.parse(savedUriStr)
            val hasPermission =
                contentResolver.persistedUriPermissions.any {
                    it.uri == uri && (it.isReadPermission || it.isWritePermission)
                }
            if (hasPermission) {
                viewModel.setRootFolder(uri)
            } else {
                viewModel.resetPermissionNeeded()
            }
        }

        setContent {
            KeepNotesTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val isEditorOpen by viewModel.isEditorOpen.collectAsState()
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val currentFilter by viewModel.currentFilter.collectAsState()
                    val labels by viewModel.labels.collectAsState()
                    val listState = rememberLazyStaggeredGridState()

                    var showCreateLabelDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    var labelToDelete by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

                    val drawerState = androidx.compose.material3.rememberDrawerState(androidx.compose.material3.DrawerValue.Closed)
                    val scope = androidx.compose.runtime.rememberCoroutineScope()

                    if (showCreateLabelDialog) {
                        com.waph1.markitnotes.ui.CreateLabelDialog(
                            onDismiss = { showCreateLabelDialog = false },
                            onConfirm = { name ->
                                viewModel.createLabel(name)
                                showCreateLabelDialog = false
                            },
                        )
                    }

                    if (labelToDelete != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { labelToDelete = null },
                            title = {
                                androidx.compose.material3.Text(
                                    androidx.compose.ui.res.stringResource(R.string.delete_label_title),
                                )
                            },
                            text = {
                                androidx.compose.material3.Text(
                                    androidx.compose.ui.res.stringResource(R.string.delete_label_message, labelToDelete!!),
                                )
                            },
                            confirmButton = {
                                androidx.compose.material3.TextButton(onClick = {
                                    val name = labelToDelete!!
                                    viewModel.deleteLabel(
                                        name = name,
                                        onSuccess = {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.label_deleted_toast),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        },
                                        onError = { error ->
                                            val localizedError =
                                                if (error == "Label must be empty to delete it") {
                                                    context.getString(R.string.error_delete_label_not_empty)
                                                } else {
                                                    error
                                                }
                                            android.widget.Toast.makeText(context, localizedError, android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                    )
                                    labelToDelete = null
                                }) {
                                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.delete_label_confirm))
                                }
                            },
                            dismissButton = {
                                androidx.compose.material3.TextButton(onClick = { labelToDelete = null }) {
                                    androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(R.string.cancel))
                                }
                            },
                        )
                    }

                    androidx.compose.material3.ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = drawerState.isOpen,
                        drawerContent = {
                            com.waph1.markitnotes.ui.AppDrawerContent(
                                currentScreen = currentScreen,
                                currentFilter = currentFilter,
                                labels = labels,
                                onScreenSelect = {
                                    viewModel.navigateTo(it)
                                    scope.launch { drawerState.close() }
                                },
                                onFilterSelect = {
                                    viewModel.setFilter(it)
                                    scope.launch { drawerState.close() }
                                },
                                onCreateLabel = { showCreateLabelDialog = true },
                                onDeleteLabel = { name -> labelToDelete = name },
                                closeDrawer = { scope.launch { drawerState.close() } },
                            )
                        },
                    ) {
                        androidx.activity.compose.BackHandler(enabled = drawerState.isOpen) {
                            scope.launch { drawerState.close() }
                        }

                        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                            when (currentScreen) {
                                MainViewModel.Screen.Dashboard -> {
                                    DashboardScreen(
                                        viewModel = viewModel,
                                        listState = listState,
                                        isDrawerOpen = drawerState.isOpen,
                                        onSelectFolder = { openDocumentTreeLauncher.launch(null) },
                                        onNoteClick = { note -> viewModel.openNote(note) },
                                        onFabClick = { viewModel.createNote() },
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                    )
                                }
                                MainViewModel.Screen.Reminders -> {
                                    com.waph1.markitnotes.ui.RemindersScreen(
                                        viewModel = viewModel,
                                        onOpenDrawer = { scope.launch { drawerState.open() } },
                                        onNoteClick = { note -> viewModel.openNote(note) },
                                    )
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = isEditorOpen,
                                enter = slideInHorizontally { width -> width },
                                exit = slideOutHorizontally { width -> width },
                                label = "EditorTransition",
                            ) {
                                val filter = viewModel.currentFilter.value
                                val label = if (filter is MainViewModel.NoteFilter.Label) filter.name else ""

                                EditorScreen(
                                    viewModel = viewModel,
                                    onBack = { viewModel.closeEditor() },
                                    initialLabel = label,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intent.getStringExtra("note_id")?.let { noteId ->
            lifecycleScope.launch {
                val note = repository.getNote(noteId)
                if (note != null) {
                    viewModel.openNote(note)
                }
            }
        }
    }
}
