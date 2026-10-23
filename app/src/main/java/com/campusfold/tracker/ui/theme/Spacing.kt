package com.campusfold.tracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The 4dp-base spacing scale. Consistent with the reference design system.
 */
object Spacing {
    val none = 0.dp

    /** Hairline separation — 2dp */
    val xxs = 2.dp

    /** Tight pairing — 4dp */
    val xs = 4.dp

    /** Related items inside a block — 8dp */
    val sm = 8.dp

    /** Step between sm and md — 12dp */
    val smd = 12.dp

    /** Workhorse padding: card padding, screen gutters, list row gaps — 16dp */
    val md = 16.dp

    /** Between distinct blocks inside a section — 24dp */
    val lg = 24.dp

    /** Between sections — 32dp */
    val xl = 32.dp

    val xxl = 48.dp
    val xxxl = 64.dp

    object Layout {
        val screenHorizontal = 16.dp
        val sectionGap = 20.dp
    }
}
