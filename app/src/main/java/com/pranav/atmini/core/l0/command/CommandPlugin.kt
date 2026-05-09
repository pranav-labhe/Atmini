package com.pranav.atmini.core.l0.command

import com.pranav.atmini.core.l0.AtminiAction

interface CommandPlugin {
    fun matches(input: String): Boolean
    fun execute(input: String): AtminiAction
}