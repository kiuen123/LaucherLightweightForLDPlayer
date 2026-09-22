package com.example.laucherlightweight

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm: PackageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        
        val allApps: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
        
        return@withContext allApps.map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                className = resolveInfo.activityInfo.name,
                icon = resolveInfo.activityInfo.loadIcon(pm)
            )
        }.sortedBy { it.label.lowercase() }
    }
}
