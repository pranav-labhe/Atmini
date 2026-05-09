package com.pranav.atmini.core.l0.command

interface AiCommandInterpreter {
    fun interpret(text: String): AtminiCommand?
}