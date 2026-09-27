package dev.xpensetracker.app

import android.app.Application
import dev.xpensetracker.app.data.AppDatabase
import dev.xpensetracker.app.data.ExpenseRepository
import dev.xpensetracker.app.data.ThemePreferences

/**
 * Application-wide composition root. There is no DI framework; dependencies are
 * constructed once here and handed to whoever needs them (Activity, receivers, workers).
 */
class ExpenseTrackerApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: ExpenseRepository
        private set

    lateinit var themePreferences: ThemePreferences
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = ExpenseRepository(database)
        themePreferences = ThemePreferences.create(this)
    }
}
