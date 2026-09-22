package com.example.laucherlightweight

import android.graphics.drawable.Drawable

sealed class LauncherItem {
    abstract val label: String
    abstract val id: String
}

data class AppInfo(
    override val label: String,
    val packageName: String,
    val className: String,
    val icon: Drawable,
    override val id: String = packageName
) : LauncherItem()

data class FolderInfo(
    override val label: String,
    val apps: MutableList<AppInfo>,
    override val id: String = "folder_${System.currentTimeMillis()}"
) : LauncherItem()
