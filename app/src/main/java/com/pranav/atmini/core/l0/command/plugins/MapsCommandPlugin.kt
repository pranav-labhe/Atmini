package com.pranav.atmini.core.l0.command.plugins

import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.core.l0.command.CommandPlugin

class MapsCommandPlugin : CommandPlugin {

    override fun matches(input: String): Boolean {
        return input.contains("maps") ||
                input.contains("open maps")
    }

    override fun execute(input: String): AtminiAction {
        return AtminiAction.OpenMaps
    }
}