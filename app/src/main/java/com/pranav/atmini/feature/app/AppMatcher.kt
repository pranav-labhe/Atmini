package com.pranav.atmini.feature.app

object AppMatcher {

    fun findApp(query: String, apps: List<InstalledApp>): InstalledApp? {

        val text = query.lowercase()

        // 1. direct match
        apps.firstOrNull {
            text.contains(it.name)
        }?.let { return it }

        // 2. fuzzy keyword match
        return apps.firstOrNull { app ->
            app.name.split(" ").any { word ->
                text.contains(word)
            }
        }
    }
}