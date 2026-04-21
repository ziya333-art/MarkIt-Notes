package com.waph1.markitnotes.data.repository

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("keepnotes_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ROOT_URI = "root_uri"
        private const val KEY_SORT_ORDER = "sort_order"
        private const val KEY_SORT_DIRECTION = "sort_direction"
        private const val KEY_VIEW_MODE = "view_mode"
    }

    enum class SortOrder {
        DATE_MODIFIED,
        TITLE,
    }

    enum class SortDirection {
        ASCENDING,
        DESCENDING,
    }

    enum class ViewMode {
        GRID,
        LIST,
    }

    fun saveSortOrder(order: SortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun getSortOrder(): SortOrder {
        val name = prefs.getString(KEY_SORT_ORDER, SortOrder.DATE_MODIFIED.name)
        return try {
            SortOrder.valueOf(name ?: SortOrder.DATE_MODIFIED.name)
        } catch (e: Exception) {
            SortOrder.DATE_MODIFIED
        }
    }

    fun saveSortDirection(direction: SortDirection) {
        prefs.edit().putString(KEY_SORT_DIRECTION, direction.name).apply()
    }

    fun getSortDirection(): SortDirection {
        val name = prefs.getString(KEY_SORT_DIRECTION, SortDirection.DESCENDING.name)
        return try {
            SortDirection.valueOf(name ?: SortDirection.DESCENDING.name)
        } catch (e: Exception) {
            SortDirection.DESCENDING
        }
    }

    fun saveViewMode(mode: ViewMode) {
        prefs.edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }

    fun getViewMode(): ViewMode {
        val name = prefs.getString(KEY_VIEW_MODE, ViewMode.GRID.name)
        return try {
            ViewMode.valueOf(name ?: ViewMode.GRID.name)
        } catch (e: Exception) {
            ViewMode.GRID
        }
    }

    fun saveRootUri(uri: String) {
        prefs.edit().putString(KEY_ROOT_URI, uri).apply()
    }

    fun getRootUri(): String? {
        return prefs.getString(KEY_ROOT_URI, null)
    }
}
