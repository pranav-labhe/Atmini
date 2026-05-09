package com.pranav.atmini.core.l0

import com.pranav.atmini.core.l0.command.CommandRegistry
import com.pranav.atmini.feature.app.InstalledApp

class AtminiBrain {

    fun process(
        text: String?,
        apps: List<InstalledApp>
    ): AtminiAction {

        val input = text ?: return AtminiAction.None

        return CommandRegistry.process(input, apps)
    }
}