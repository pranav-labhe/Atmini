package com.pranav.atmini.core.l0

data class ReflexResult(
    val action: AtminiAction,
    val success: Boolean = true,
    val spokenText: String
)