package com.pranav.atmini.core.l0

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

class L0ReflexSystem {

    fun execute(context: Context, action: AtminiAction): ReflexResult {

        return when (action) {

            is AtminiAction.OpenApp -> {

                val intent = context.packageManager
                    .getLaunchIntentForPackage(action.packageName)

                val appName = action.appName // fallback for now

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)

                    ReflexResult(
                        action = action,
                        success = true,
                        spokenText = "Opening $appName"
                    )
                } else {
                    ReflexResult(
                        action = action,
                        success = false,
                        spokenText = "App not found"
                    )
                }
            }

            AtminiAction.OpenMaps -> {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=Nagpur")
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                ReflexResult(action, true, "Opening maps")
            }

            AtminiAction.AskAI -> {
                ReflexResult(action, true, "Let me think about that")
            }

            AtminiAction.None -> {
                ReflexResult(action, true, "")
            }
        }
    }
}