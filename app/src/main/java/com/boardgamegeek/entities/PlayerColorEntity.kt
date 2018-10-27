package com.boardgamegeek.entities

import com.boardgamegeek.extensions.asColorRgb

data class PlayerColorEntity(
        val description: String,
        val sortOrder: Int = 0
) {
    val rgb = description.asColorRgb()
}