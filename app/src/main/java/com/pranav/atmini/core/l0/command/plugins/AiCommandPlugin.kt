package com.pranav.atmini.core.l0.command.plugins

import com.pranav.atmini.core.l0.AtminiAction
import com.pranav.atmini.core.l0.command.CommandPlugin

class AiCommandPlugin : CommandPlugin {

    override fun matches(input: String): Boolean {
        return input.contains("ai") ||
                input.contains("ask ai")
    }

    override fun execute(input: String): AtminiAction {
        return AtminiAction.AskAI
    }
}