package com.waph1.markitnotes.data.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.waph1.markitnotes.MainActivity
import com.waph1.markitnotes.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val CHANNEL_ID = "reminders_channel"
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_TITLE = "note_title"
        const val EXTRA_CONTENT = "note_content"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val metadataManager = com.waph1.markitnotes.data.repository.MetadataManager(context)
            val repository = com.waph1.markitnotes.data.repository.RoomNoteRepository(context, metadataManager)
            val scheduler = NotificationScheduler(context)

            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val notes = repository.getAllNotesWithArchive().first()
                    val now = System.currentTimeMillis()
                    notes.forEach { note ->
                        if (note.reminder != null && note.reminder > now) {
                            scheduler.schedule(note)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"

        val pendingResult = goAsync()
        val metadataManager = com.waph1.markitnotes.data.repository.MetadataManager(context)
        val repository = com.waph1.markitnotes.data.repository.RoomNoteRepository(context, metadataManager)

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val note = repository.getNote(noteId)
                android.util.Log.d("ReminderReceiver", "Received reminder for note: $noteId, found: ${note != null}")
                if (note != null) {
                    if (note.isTrashed) {
                        android.util.Log.d("ReminderReceiver", "Note is trashed, ignoring")
                    } else {
                        if (note.isArchived) {
                            android.util.Log.d("ReminderReceiver", "Note is archived, unarchiving")
                            repository.restoreNote(noteId)
                        }

                        // Show Notification
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        createNotificationChannel(notificationManager)

                        val openIntent =
                            Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                putExtra(EXTRA_NOTE_ID, noteId)
                            }

                        val pendingIntent =
                            PendingIntent.getActivity(
                                context,
                                noteId.hashCode(),
                                openIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                            )

                        val notification =
                            NotificationCompat.Builder(context, CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_launcher_foreground)
                                .setContentTitle(title)
                                .setPriority(NotificationCompat.PRIORITY_MAX)
                                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                                .setContentIntent(pendingIntent)
                                .setAutoCancel(true)
                                .setDefaults(NotificationCompat.DEFAULT_ALL)
                                .build()

                        notificationManager.notify(noteId.hashCode(), notification)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reminders"
            val descriptionText = "Note Reminders"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    enableLights(true)
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
