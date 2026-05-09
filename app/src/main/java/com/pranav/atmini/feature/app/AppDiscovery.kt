package com.pranav.atmini.feature.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppDiscovery {

    fun resolveAppPackage(
        context: Context,
        query: String
    ): String? {

        val apps = getInstalledApps(context)
        val normalizedQuery = query.lowercase().trim()

        return apps.firstOrNull { app ->
            app.name.contains(normalizedQuery) ||
                    app.appName.contains(normalizedQuery) ||
                    normalizedQuery.contains(app.name)
        }?.packageName
    }

    fun getInstalledApps(context: Context): List<InstalledApp> {

        val pm: PackageManager = context.packageManager

        val intent = Intent(Intent.ACTION_MAIN, null)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)

        val apps = pm.queryIntentActivities(intent, 0)

        return apps.map {
            InstalledApp(
                name = it.loadLabel(pm).toString().lowercase(),
                appName = it.loadLabel(pm).toString().lowercase(),
                packageName = it.activityInfo.packageName
            )
        }
    }
}