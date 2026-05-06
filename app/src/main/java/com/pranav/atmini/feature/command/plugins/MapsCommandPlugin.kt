package com.pranav.atmini.feature.command.plugins

import com.pranav.atmini.feature.action.AtminiAction
import com.pranav.atmini.feature.command.CommandPlugin

class MapsCommandPlugin : CommandPlugin {

    override fun matches(input: String): Boolean {
        return input.contains("maps") ||
                input.contains("open maps")
    }

    override fun execute(input: String): AtminiAction {
        return AtminiAction.OpenMaps
    }
}