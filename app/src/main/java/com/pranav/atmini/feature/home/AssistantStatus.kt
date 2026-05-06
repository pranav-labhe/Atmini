package com.pranav.atmini.feature.home

sealed class AssistantStatus {

    object Idle : AssistantStatus()
    object Listening : AssistantStatus()
    object Thinking : AssistantStatus()
    object Acting : AssistantStatus()
}