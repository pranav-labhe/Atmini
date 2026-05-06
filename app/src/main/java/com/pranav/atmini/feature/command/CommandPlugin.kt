package com.pranav.atmini.feature.command

import com.pranav.atmini.feature.action.AtminiAction

interface CommandPlugin {
    fun matches(input: String): Boolean
    fun execute(input: String): AtminiAction
}