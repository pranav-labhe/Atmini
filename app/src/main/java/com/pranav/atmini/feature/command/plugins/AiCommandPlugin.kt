package com.pranav.atmini.feature.command.plugins

import com.pranav.atmini.feature.action.AtminiAction
import com.pranav.atmini.feature.command.CommandPlugin

class AiCommandPlugin : CommandPlugin {

    override fun matches(input: String): Boolean {
        return input.contains("ai") ||
                input.contains("ask ai")
    }

    override fun execute(input: String): AtminiAction {
        return AtminiAction.AskAI
    }
}