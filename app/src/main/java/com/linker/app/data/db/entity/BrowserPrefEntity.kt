package com.linker.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per installed browser package the user has ever seen in the chooser. A browser with no
 * row here just uses defaults (visible, system label, appended to the end of the order).
 */
@Entity(tableName = "browser_prefs")
data class BrowserPrefEntity(
    @PrimaryKey val packageName: String,
    val customLabel: String? = null,
    val hidden: Boolean = false,
    val orderIndex: Int = 0
)
