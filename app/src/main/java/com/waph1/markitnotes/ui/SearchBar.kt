package com.waph1.markitnotes.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.waph1.markitnotes.R
import com.waph1.markitnotes.data.repository.PrefsManager

@Composable
fun SearchBar(viewModel: MainViewModel) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val sortDirection by viewModel.sortDirection.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ... (Search Input and Clear Button remain the same)
        BasicTextField(
            value = searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (searchQuery.isNotEmpty()) {
            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.clear_search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // View Mode Toggle Button
        IconButton(onClick = {
            viewModel.setViewMode(
                if (viewMode == PrefsManager.ViewMode.GRID) {
                    PrefsManager.ViewMode.LIST
                } else {
                    PrefsManager.ViewMode.GRID
                },
            )
        }) {
            Icon(
                imageVector = if (viewMode == PrefsManager.ViewMode.GRID) Icons.Default.ViewStream else Icons.Default.GridView,
                contentDescription =
                    if (viewMode == PrefsManager.ViewMode.GRID) {
                        stringResource(
                            R.string.view_list,
                        )
                    } else {
                        stringResource(R.string.view_grid)
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Sort Button (Right Edge)
        Box {
            IconButton(onClick = { showSortMenu = true }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.sort_notes),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.date)) },
                    trailingIcon = { if (sortOrder == PrefsManager.SortOrder.DATE_MODIFIED) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortOrder(PrefsManager.SortOrder.DATE_MODIFIED)
                        showSortMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_title)) },
                    trailingIcon = { if (sortOrder == PrefsManager.SortOrder.TITLE) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortOrder(PrefsManager.SortOrder.TITLE)
                        showSortMenu = false
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_ascending)) },
                    trailingIcon = { if (sortDirection == PrefsManager.SortDirection.ASCENDING) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortDirection(PrefsManager.SortDirection.ASCENDING)
                        showSortMenu = false
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.sort_descending)) },
                    trailingIcon = { if (sortDirection == PrefsManager.SortDirection.DESCENDING) Icon(Icons.Default.Check, null) },
                    onClick = {
                        viewModel.setSortDirection(PrefsManager.SortDirection.DESCENDING)
                        showSortMenu = false
                    },
                )
            }
        }
    }
}
