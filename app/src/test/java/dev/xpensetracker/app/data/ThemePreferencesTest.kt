package dev.xpensetracker.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.xpensetracker.app.ui.theme.AccentTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The accent preference is the only setting the app persists, so the round trip and the stale-value
 * fallback are pinned here.
 */
@RunWith(RobolectricTestRunner::class)
class ThemePreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    /**
     * The file is named but never created: DataStore writes it on first use, and an existing empty
     * file reads as corruption. Each call gets its own name so no two tests share state.
     */
    private fun TestScope.newStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            produceFile = { File(tempFolder.root, "$name.preferences_pb") },
        )

    @Test
    fun `an unset preference reads as the default accent`() = runTest {
        val preferences = ThemePreferences(newStore("unset"))

        assertEquals(AccentTheme.DEFAULT, preferences.accent.first())
    }

    @Test
    fun `a selected accent is read back`() = runTest {
        val preferences = ThemePreferences(newStore("selected"))

        preferences.setAccent(AccentTheme.AZURE)

        assertEquals(AccentTheme.AZURE, preferences.accent.first())
    }

    @Test
    fun `the most recent selection wins`() = runTest {
        val preferences = ThemePreferences(newStore("latest"))

        preferences.setAccent(AccentTheme.LIME)
        preferences.setAccent(AccentTheme.ORCHID)

        assertEquals(AccentTheme.ORCHID, preferences.accent.first())
    }

    // A stored id can go stale through a downgrade or a hand-edited file, and an unreadable colour
    // preference must never be a reason the app fails to draw.
    @Test
    fun `an unrecognised stored id falls back to the default`() = runTest {
        val store = newStore("stale")
        store.edit { it[stringPreferencesKey("accent_theme")] = "chartreuse" }

        assertEquals(AccentTheme.DEFAULT, ThemePreferences(store).accent.first())
    }

    @Test
    fun `accent ids are unique and stable across renames`() {
        val ids = AccentTheme.entries.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(listOf("ember", "lime", "azure", "orchid"), ids)
    }
}
