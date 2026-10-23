package com.campusfold.tracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Semantic dimensions layered on top of the Spacing scale.
 */
object Dimensions {

    object Padding {
        val content = Spacing.Layout.screenHorizontal
        val card = 16.dp
        val cardCompact = 12.dp
        val listRowVertical = 12.dp
        val dialog = 24.dp
        val empty = 32.dp
        val fab = 16.dp
    }

    object CornerRadius {
        val small = 8.dp
        val medium = 12.dp
        val large = 16.dp
        val extraLarge = 24.dp
        val pill = 32.dp
    }

    object Icon {
        val small = 16.dp
        val medium = 24.dp
        val large = 32.dp
        val extraLarge = 48.dp
    }

    object Elevation {
        val none = 0.dp
        val card = 0.dp
        val raisedCard = 2.dp
        val bottomBar = 4.dp
        val fab = 6.dp
        val dialog = 8.dp
    }

    object Alpha {
        const val high = 0.90f
        const val medium = 0.55f
        const val disabled = 0.38f
    }
}
