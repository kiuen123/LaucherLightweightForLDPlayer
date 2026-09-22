package com.example.laucherlightweight

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class StorageHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    fun getGridColumns(): Int =
        prefs.getInt(KEY_GRID_COLUMNS, DEFAULT_GRID_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    fun saveGridColumns(columns: Int) {
        prefs.edit()
            .putInt(KEY_GRID_COLUMNS, columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
            .apply()
    }

    fun getIconSizeDp(): Int {
        val saved = prefs.getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE_DP)
        return ICON_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - saved) } ?: DEFAULT_ICON_SIZE_DP
    }

    fun saveIconSizeDp(sizeDp: Int) {
        val normalized = ICON_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - sizeDp) } ?: DEFAULT_ICON_SIZE_DP
        prefs.edit().putInt(KEY_ICON_SIZE, normalized).apply()
    }

    fun iconSizePickerIndex(): Int {
        val size = getIconSizeDp()
        return ICON_SIZE_OPTIONS.indexOf(size).coerceAtLeast(0)
    }

    fun iconSizeFromPickerIndex(index: Int): Int =
        ICON_SIZE_OPTIONS[index.coerceIn(0, ICON_SIZE_OPTIONS.lastIndex)]

    fun getLabelTextSizeSp(): Int =
        prefs.getInt(KEY_LABEL_TEXT_SIZE, DEFAULT_LABEL_TEXT_SIZE_SP)
            .coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP)

    fun saveLabelTextSizeSp(sizeSp: Int) {
        prefs.edit()
            .putInt(KEY_LABEL_TEXT_SIZE, sizeSp.coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP))
            .apply()
    }

    fun getFolderGridColumns(): Int =
        prefs.getInt(KEY_FOLDER_GRID_COLUMNS, DEFAULT_FOLDER_GRID_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS)

    fun saveFolderGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_FOLDER_GRID_COLUMNS, columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS)).apply()
    }

    fun getFolderIconSizeDp(): Int {
        val saved = prefs.getInt(KEY_FOLDER_ICON_SIZE, DEFAULT_FOLDER_ICON_SIZE_DP)
        return ICON_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - saved) } ?: DEFAULT_FOLDER_ICON_SIZE_DP
    }

    fun saveFolderIconSizeDp(sizeDp: Int) {
        val normalized = ICON_SIZE_OPTIONS.minByOrNull { kotlin.math.abs(it - sizeDp) } ?: DEFAULT_FOLDER_ICON_SIZE_DP
        prefs.edit().putInt(KEY_FOLDER_ICON_SIZE, normalized).apply()
    }

    fun folderIconSizePickerIndex(): Int =
        ICON_SIZE_OPTIONS.indexOf(getFolderIconSizeDp()).coerceAtLeast(0)

    fun getFolderLabelTextSizeSp(): Int =
        prefs.getInt(KEY_FOLDER_LABEL_TEXT_SIZE, DEFAULT_FOLDER_LABEL_TEXT_SIZE_SP)
            .coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP)

    fun saveFolderLabelTextSizeSp(sizeSp: Int) {
        prefs.edit().putInt(
            KEY_FOLDER_LABEL_TEXT_SIZE,
            sizeSp.coerceIn(MIN_LABEL_TEXT_SIZE_SP, MAX_LABEL_TEXT_SIZE_SP)
        ).apply()
    }

    fun getWallpaperUri(): String? = prefs.getString(KEY_WALLPAPER_URI, null)

    fun saveWallpaperUri(uri: String) {
        prefs.edit().putString(KEY_WALLPAPER_URI, uri).apply()
    }

    fun clearWallpaperUri() {
        prefs.edit().remove(KEY_WALLPAPER_URI).apply()
    }

    fun saveAppOrder(apps: List<AppInfo>) {
        val packageNames = JSONArray()
        apps.forEach { packageNames.put(it.packageName) }
        prefs.edit().putString(KEY_APP_ORDER, packageNames.toString()).apply()
    }

    fun sortAppsBySavedOrder(apps: List<AppInfo>): List<AppInfo> {
        val savedOrder = prefs.getString(KEY_APP_ORDER, null) ?: return apps
        return try {
            val packageIndexes = mutableMapOf<String, Int>()
            val packageNames = JSONArray(savedOrder)
            for (index in 0 until packageNames.length()) {
                packageIndexes[packageNames.getString(index)] = index
            }
            apps.sortedBy { packageIndexes[it.packageName] ?: Int.MAX_VALUE }
        } catch (_: Exception) {
            apps
        }
    }

    fun saveFolders(folders: List<FolderInfo>) {
        val jsonArray = JSONArray()
        for (folder in folders) {
            val folderObj = JSONObject()
            folderObj.put("id", folder.id)
            folderObj.put("label", folder.label)
            
            val appsArray = JSONArray()
            for (app in folder.apps) {
                appsArray.put(app.packageName)
            }
            folderObj.put("apps", appsArray)
            jsonArray.put(folderObj)
        }
        prefs.edit().putString("folders", jsonArray.toString()).apply()
    }

    fun loadFolders(allApps: List<AppInfo>): List<FolderInfo> {
        val jsonString = prefs.getString("folders", null) ?: return emptyList()
        val folders = mutableListOf<FolderInfo>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val folderObj = jsonArray.getJSONObject(i)
                val id = folderObj.getString("id")
                val label = folderObj.getString("label")
                val appsArray = folderObj.getJSONArray("apps")
                
                val folderApps = mutableListOf<AppInfo>()
                for (j in 0 until appsArray.length()) {
                    val packageName = appsArray.getString(j)
                    val app = allApps.find { it.packageName == packageName }
                    if (app != null) {
                        folderApps.add(app)
                    }
                }
                folders.add(FolderInfo(label, folderApps, id))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return folders
    }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 8
        const val DEFAULT_GRID_COLUMNS = 4
        const val DEFAULT_ICON_SIZE_DP = 64
        val ICON_SIZE_OPTIONS = intArrayOf(40, 48, 56, 64, 72, 80, 88, 96, 104, 112)
        const val MIN_LABEL_TEXT_SIZE_SP = 10
        const val MAX_LABEL_TEXT_SIZE_SP = 20
        const val DEFAULT_LABEL_TEXT_SIZE_SP = 14
        const val DEFAULT_FOLDER_GRID_COLUMNS = 4
        const val DEFAULT_FOLDER_ICON_SIZE_DP = 48
        const val DEFAULT_FOLDER_LABEL_TEXT_SIZE_SP = 12

        private const val KEY_GRID_COLUMNS = "grid_columns"
        private const val KEY_ICON_SIZE = "icon_size_dp"
        private const val KEY_WALLPAPER_URI = "wallpaper_uri"
        private const val KEY_APP_ORDER = "app_order"
        private const val KEY_LABEL_TEXT_SIZE = "label_text_size_sp"
        private const val KEY_FOLDER_GRID_COLUMNS = "folder_grid_columns"
        private const val KEY_FOLDER_ICON_SIZE = "folder_icon_size_dp"
        private const val KEY_FOLDER_LABEL_TEXT_SIZE = "folder_label_text_size_sp"
    }
}
