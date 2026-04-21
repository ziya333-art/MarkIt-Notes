package com.waph1.markitnotes.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onRemove: () -> Unit,
    initialTimestamp: Long? = null,
) {
    var showingTimePicker by remember { mutableStateOf(false) }

    val initialMillis = initialTimestamp ?: System.currentTimeMillis()
    val dateState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    // Initializing TimePicker state with hour/minute from initialTimestamp
    val initialCalendar = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val timeState =
        rememberTimePickerState(
            initialHour = initialCalendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = initialCalendar.get(Calendar.MINUTE),
        )

    val haptic = LocalHapticFeedback.current

    // Haptic feedback for date selection
    LaunchedEffect(dateState.selectedDateMillis) {
        if (dateState.selectedDateMillis != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // Haptic feedback for time selection (hour/minute change)
    LaunchedEffect(timeState.hour, timeState.minute) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    if (showingTimePicker) {
        TimePickerDialog(
            onDismiss = { showingTimePicker = false },
            onConfirm = {
                val selectedDateMillis = dateState.selectedDateMillis ?: System.currentTimeMillis()

                val calendar =
                    Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, timeState.hour)
                        set(Calendar.MINUTE, timeState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onConfirm(calendar.timeInMillis)
            },
            onRemove = {
                onRemove()
                onDismiss()
            },
            showRemove = initialTimestamp != null,
        ) {
            TimePicker(state = timeState)
        }
    } else {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                Row {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    TextButton(
                        onClick = {
                            if (dateState.selectedDateMillis != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showingTimePicker = true
                            }
                        },
                        enabled = dateState.selectedDateMillis != null,
                    ) {
                        Text("Next")
                    }
                }
            },
            dismissButton = {
                if (initialTimestamp != null) {
                    TextButton(onClick = {
                        onRemove()
                        onDismiss()
                    }) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
fun TimePickerDialog(
    title: String = "Select Time",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    onRemove: () -> Unit,
    showRemove: Boolean,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        dismissButton = {
            if (showRemove) {
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onDismiss() }) {
                    Text("Cancel")
                }
                TextButton(onClick = { onConfirm() }) {
                    Text("OK")
                }
            }
        },
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                content()
            }
        },
    )
}
