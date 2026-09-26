package dev.expensetracker.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider

/** In-memory Room database builder shared by DAO/repository Robolectric tests. */
object RoomTestDatabase {
    fun create(): AppDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
}
