package dev.xpensetracker.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Base palette (architecture-neutral; owned solely by design-system-and-navigation).
val BackgroundBase = Color(0xFF0D0D0D)
val SurfaceCard = Color(0xFF151515)
val SurfaceElevated = Color(0xFF1E1E1E)

/**
 * The one interactive hue: CTAs, the selected tab, the spend ring, active chips.
 *
 * Reads through [LocalAccentTheme] rather than naming a colour, because the user picks it in
 * Settings. It stays a property with the same name it had as a constant so that every call site
 * already written as `color = AccentPrimary` kept working when it became a preference; the only
 * places that needed changing were the ones reading it outside composition, which now have to
 * hoist it, and that is a fair price for making the value dynamic.
 */
val AccentPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentTheme.current.primary

val AccentPrimaryMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = LocalAccentTheme.current.muted
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB3B3B3)
val TextTertiary = Color(0xFF7A7A7A)

/**
 * Rose rather than the previous orange-leaning red: against a warm accent, a destructive action
 * has to be distinguishable from the primary call to action at a glance, and two colours 18 degrees
 * apart on the wheel are not.
 */
val DangerRed = Color(0xFFF43F5E)
val PositiveGreen = Color(0xFF4ADE80)
val Divider = Color(0xFF262626)

/**
 * Category hues are NOT defined here. A category's color lives on its `categories` row as
 * `colorArgb` (architecture.md #data-representation), so the donut, the category list, and
 * transaction avatars all read one value and a user-created category gets a real color.
 *
 * This is only the swatch set offered when creating or editing one.
 *
 * No swatch may be the *exact* value of any [AccentTheme]. Hue separation was the stronger rule
 * while there was one fixed accent; with four selectable ones, sixteen swatches cannot avoid all
 * of them, and pretending otherwise would mean either a thin swatch set or a false promise. What
 * still holds is that the accent is never the only signal that something is interactive — shape,
 * placement, and labels carry it too — so a category that merely sits near the active accent
 * cannot be mistaken for a control.
 */
val categorySwatches: List<Long> = listOf(
    0xFFEC4899,
    0xFF38BDF8,
    0xFF34D399,
    0xFF4ADE80,
    0xFFF59E0B,
    0xFFFB923C,
    0xFFA78BFA,
    0xFFC084FC,
    0xFF60A5FA,
    0xFFFBBF24,
    0xFF818CF8,
    0xFF8FA6C4,
    0xFFF87171,
    0xFF2DD4BF,
    0xFFB0B0B0,
    0xFF6B7280,
)

/** Packed ARGB from a `categories` row to a Compose color; the alpha bits are forced opaque. */
fun Long.toCategoryColor(): Color = Color(this or 0xFF000000L)
