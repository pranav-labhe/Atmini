package com.pranav.atmini.core.l0.command

import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.feature.app.AppMatcher
import com.pranav.atmini.feature.app.InstalledApp

object CommandRegistry {

    fun process(
        input: String?,
        apps: List<InstalledApp>
    ): AtminiAction {

        if (input.isNullOrBlank()) return AtminiAction.None

        val text = normalize(input)

        // -------------------------
        // 1. APP INTENT (FAST PATH)
        // -------------------------
        AppMatcher.findApp(text, apps)?.let {
            return AtminiAction.OpenApp(it.packageName, it.appName)
        }

        // -------------------------
        // 2. MAPS INTENT
        // -------------------------
        if (text.contains("map") || text.contains("navigate") || text.contains("direction")) {
            return AtminiAction.OpenMaps
        }

        // -------------------------
        // 3. AI INTENT (future hook)
        // -------------------------
        if (text.contains("ask") || text.contains("ai")) {
            return AtminiAction.AskAI
        }

        // -------------------------
        // 4. FALLBACK
        // -------------------------
        return AtminiAction.None
    }

    // -------------------------
    // NORMALIZATION (IMPORTANT FOR FUTURE CACHE LAYER)
    // -------------------------
    private fun normalize(input: String): String {
        return input
            .lowercase()
            .trim()
            .replace("open ", "")
            .replace("launch ", "")
    }
}