package com.pranav.atmini.feature.home

import com.pranav.atmini.core.l0.AtminiAction

object ActionUiMapper {

    fun toUiText(action: AtminiAction, spokenText: String): String {

        return when (action) {

            AtminiAction.OpenMaps ->
                "📍 Taking you to maps..."

            AtminiAction.AskAI ->
                "🤖 Let me think about that..."

            is AtminiAction.OpenApp ->
                "🚀 ${spokenText}..."

            AtminiAction.None ->
                "🟢 Ready"
        }
    }
}