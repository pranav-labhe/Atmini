package com.pranav.atmini.feature.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object AppDiscovery {

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