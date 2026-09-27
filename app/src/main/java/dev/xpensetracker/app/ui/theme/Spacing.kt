package dev.xpensetracker.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The spacing step. Screens reference these rather than inline dp literals, which is what keeps
 * vertical rhythm consistent across four independently-edited screens.
 */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal page gutter, identical on every screen. */
    val gutter = 20.dp

    /** Minimum interactive size; below this a control is hard to hit reliably. */
    val minTouchTarget = 48.dp
}
