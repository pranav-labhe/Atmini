package com.pranav.atmini.feature.action

sealed class AtminiAction {

    object OpenMaps : AtminiAction()
    object AskAI : AtminiAction()

    data class OpenApp(val packageName: String,val appName: String) : AtminiAction()

    object None : AtminiAction()
}