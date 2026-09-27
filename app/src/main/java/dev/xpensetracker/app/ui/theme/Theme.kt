package dev.xpensetracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The accent in force for this subtree.
 *
 * `static` rather than a regular composition local: the value changes only when the user picks a
 * different theme, and when that happens every surface should be rebuilt anyway. Paying for
 * fine-grained invalidation on a value read in dozens of places, to serve an event that happens
 * once in a while, would be the wrong trade.
 */
val LocalAccentTheme = staticCompositionLocalOf { AccentTheme.DEFAULT }

/**
 * Dark-only by design (see blueprints/design-system-and-navigation.md gaps).
 *
 * Only the primary slots follow the selected accent. `error` and the income green stay fixed
 * because they mean something — a preference may not make a destructive action look ordinary.
 */
private fun colorSchemeFor(accent: AccentTheme) = darkColorScheme(
    primary = accent.primary,
    onPrimary = BackgroundBase,
    primaryContainer = accent.muted,
    onPrimaryContainer = TextPrimary,
    secondary = accent.primary,
    background = BackgroundBase,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    error = DangerRed,
    onError = TextPrimary,
    outline = Divider,
    outlineVariant = Divider,
)

@Composable
fun ExpenseTrackerTheme(
    accent: AccentTheme = AccentTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val colorScheme = remember(accent) { colorSchemeFor(accent) }
    CompositionLocalProvider(LocalAccentTheme provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ExpenseTypography,
            shapes = ExpenseShapes,
            content = content,
        )
    }
}
