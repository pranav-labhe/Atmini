package com.pranav.atmini.core.l0.command

sealed class AtminiCommand {

    object Unknown : AtminiCommand()

    object OpenMaps : AtminiCommand()

    data class OpenApp(val appName: String) : AtminiCommand()

    object NavigateHome : AtminiCommand()

    data class PlayMedia(
        val type: MediaType,
        val query: String
    ) : AtminiCommand()

    data class Call(val contact: String) : AtminiCommand()

    data class SendMessage(
        val contact: String,
        val message: String
    ) : AtminiCommand()

    object AskAI : AtminiCommand()

    enum class MediaType {
        MUSIC, VIDEO
    }
}