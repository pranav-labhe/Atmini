package com.pranav.atmini.feature.action

import android.app.Activity
import android.content.Intent
import android.net.Uri

object ActionDispatcher {

    fun dispatch(activity: Activity, action: AtminiAction) {

        when (action) {

            is AtminiAction.OpenApp -> {
                val packageName = action.packageName

                val intent = activity.packageManager
                    .getLaunchIntentForPackage(packageName)

                if (intent != null) {
                    activity.startActivity(intent)
                } else {
                    android.util.Log.d("Atmini", "App not installed: $packageName")
                }
            }

            AtminiAction.OpenMaps -> {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=Nagpur")
                )
                activity.startActivity(intent)
            }

            AtminiAction.AskAI -> {
                android.util.Log.d("Atmini", "AI triggered")
            }

            AtminiAction.None -> {
                // do nothing
            }
        }
    }
}