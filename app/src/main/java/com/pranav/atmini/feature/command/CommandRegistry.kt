package com.pranav.atmini.feature.command

import com.pranav.atmini.feature.action.AtminiAction
import com.pranav.atmini.feature.app.AppMatcher
import com.pranav.atmini.feature.app.InstalledApp
import com.pranav.atmini.feature.command.plugins.AiCommandPlugin
import com.pranav.atmini.feature.command.plugins.MapsCommandPlugin

object CommandRegistry {

    private val plugins: List<CommandPlugin> = listOf(
        MapsCommandPlugin(),
        AiCommandPlugin()
    )

    fun process(
        input: String?,
        apps: List<InstalledApp>
    ): AtminiAction {

        if (input.isNullOrBlank()) return AtminiAction.None

        val text = input.lowercase()

        // 🔥 AUTO APP DISCOVERY
        AppMatcher.findApp(text, apps)?.let {
            return AtminiAction.OpenApp(it.packageName,it.appName)
        }

        return AtminiAction.None
    }
}