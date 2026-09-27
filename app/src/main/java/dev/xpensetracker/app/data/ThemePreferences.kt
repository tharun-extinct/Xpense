package dev.xpensetracker.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.xpensetracker.app.ui.theme.AccentTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * The user's accent choice, and the only preference the app stores.
 *
 * DataStore rather than a `settings` row in Room: this is a device-local display preference, not
 * part of the financial record, and keeping it out of the database means it can never appear in a
 * migration that must not lose money.
 *
 * Takes a [DataStore] rather than a `Context` so tests can hand it a temporary file instead of
 * writing into the app's real preferences.
 */
class ThemePreferences(private val store: DataStore<Preferences>) {

    val accent: Flow<AccentTheme> = store.data.map { AccentTheme.fromId(it[ACCENT_KEY]) }

    suspend fun setAccent(theme: AccentTheme) {
        store.edit { it[ACCENT_KEY] = theme.id }
    }

    companion object {
        private val ACCENT_KEY = stringPreferencesKey("accent_theme")

        fun create(context: Context): ThemePreferences =
            ThemePreferences(context.applicationContext.settingsDataStore)
    }
}
