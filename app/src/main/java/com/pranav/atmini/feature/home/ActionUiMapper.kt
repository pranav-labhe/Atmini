package com.pranav.atmini.feature.home

import com.pranav.atmini.feature.action.AtminiAction

object ActionUiMapper {

    fun toUiText(action: AtminiAction): String {

        return when (action) {

            AtminiAction.OpenMaps ->
                "📍 Taking you to maps..."

            AtminiAction.AskAI ->
                "🤖 Let me think about that..."

            is AtminiAction.OpenApp ->
                "🚀 Opening ${action.appName}..."

            AtminiAction.None ->
                "🟢 Ready"
        }
    }
}