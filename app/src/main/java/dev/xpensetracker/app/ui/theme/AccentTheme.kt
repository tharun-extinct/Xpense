package dev.xpensetracker.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The selectable accent hues. Exactly one is active at a time and it is the only interactive colour
 * in the app, so this enum is the whole of "theming" here — surfaces, text, and the semantic
 * income/danger colours stay fixed, because those carry meaning that a preference must not alter.
 *
 * Each entry ships a [muted] companion for the Material `primaryContainer` slot. It is a darkened
 * form of the same hue rather than a separately chosen colour, so adding a theme is one decision,
 * not two that can drift apart.
 *
 * [id] is what gets persisted. It is a stable string and not the enum's `name`, so the constants
 * can be renamed without silently resetting every existing user's choice.
 */
enum class AccentTheme(
    val id: String,
    val displayName: String,
    val primary: Color,
    val muted: Color,
) {
    /** The default since the palette moved off lime. Warm, and the highest-contrast of the four. */
    EMBER("ember", "Ember", Color(0xFFFF8A3D), Color(0xFF8A4519)),

    /** The original accent, kept selectable because it is what early builds looked like. */
    LIME("lime", "Lime", Color(0xFFA8E84A), Color(0xFF5E7F2A)),

    /** Cool counterpart to Ember: nothing else in the app is near this hue. */
    AZURE("azure", "Azure", Color(0xFF22D3EE), Color(0xFF15707E)),

    /** The one cool-but-warm option, far enough from the rose danger colour to stay distinct. */
    ORCHID("orchid", "Orchid", Color(0xFFE879F9), Color(0xFF7E3F86)),
    ;

    companion object {
        val DEFAULT = EMBER

        /**
         * Unknown ids resolve to [DEFAULT] rather than throwing: the stored value is data that a
         * downgrade or a hand-edited preferences file can make stale, and an unreadable colour
         * preference is never a good enough reason to fail to draw the app.
         */
        fun fromId(id: String?): AccentTheme = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
