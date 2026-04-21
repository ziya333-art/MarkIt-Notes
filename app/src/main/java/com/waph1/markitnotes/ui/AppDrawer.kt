package com.waph1.markitnotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.waph1.markitnotes.R

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AppDrawerContent(
    currentScreen: MainViewModel.Screen,
    currentFilter: MainViewModel.NoteFilter,
    labels: List<String>,
    onScreenSelect: (MainViewModel.Screen) -> Unit,
    onFilterSelect: (MainViewModel.NoteFilter) -> Unit,
    onCreateLabel: () -> Unit,
    onDeleteLabel: (String) -> Unit,
    closeDrawer: () -> Unit,
) {
    ModalDrawerSheet {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineMedium,
            )
            HorizontalDivider()

            // Notes (All)
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.all_notes)) },
                icon = { Icon(Icons.Outlined.Description, contentDescription = null) },
                selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.All,
                onClick = {
                    onScreenSelect(MainViewModel.Screen.Dashboard)
                    onFilterSelect(MainViewModel.NoteFilter.All)
                    closeDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Reminders
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.reminders)) },
                icon = { Icon(Icons.Outlined.Notifications, contentDescription = null) },
                selected = currentScreen is MainViewModel.Screen.Reminders,
                onClick = {
                    onScreenSelect(MainViewModel.Screen.Reminders)
                    closeDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Labels
            val visibleLabels = labels.filter { it != "Inbox" }
            if (visibleLabels.isNotEmpty()) {
                Text(
                    stringResource(R.string.labels),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                visibleLabels.forEach { label ->
                    val haptic = LocalHapticFeedback.current
                    val isSelected = currentScreen is MainViewModel.Screen.Dashboard && (currentFilter as? MainViewModel.NoteFilter.Label)?.name == label

                    Box(
                        modifier =
                            Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .combinedClickable(
                                    onClick = {
                                        onScreenSelect(MainViewModel.Screen.Dashboard)
                                        onFilterSelect(MainViewModel.NoteFilter.Label(label))
                                        closeDrawer()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onDeleteLabel(label)
                                    },
                                )
                                .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                    }
                }
            }

            // New Label Button
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.create_new_label)) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                selected = false,
                onClick = {
                    onCreateLabel()
                    closeDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Archive
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.archive)) },
                selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Archive,
                onClick = {
                    onScreenSelect(MainViewModel.Screen.Dashboard)
                    onFilterSelect(MainViewModel.NoteFilter.Archive)
                    closeDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Trash
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.trash)) },
                selected = currentScreen is MainViewModel.Screen.Dashboard && currentFilter is MainViewModel.NoteFilter.Trash,
                onClick = {
                    onScreenSelect(MainViewModel.Screen.Dashboard)
                    onFilterSelect(MainViewModel.NoteFilter.Trash)
                    closeDrawer()
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}
