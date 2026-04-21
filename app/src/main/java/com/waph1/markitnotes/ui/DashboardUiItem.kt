package com.waph1.markitnotes.ui

import com.waph1.markitnotes.data.model.Note

sealed interface DashboardUiItem {
    val key: String

    data class NoteItem(val note: Note) : DashboardUiItem {
        override val key: String = note.file.path
    }

    data class HeaderItem(val type: HeaderType) : DashboardUiItem {
        override val key: String = "header_${type.name}"
    }

    object SpacerItem : DashboardUiItem {
        override val key: String = "spacer_bottom"
    }

    enum class HeaderType {
        PINNED,
        OTHERS,
        ARCHIVED,
        SEARCH_RESULTS,
        SEARCH_EVERYWHERE,
    }
}
