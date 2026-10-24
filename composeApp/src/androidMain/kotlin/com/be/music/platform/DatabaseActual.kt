package com.be.music.platform

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.be.music.BeMusicApplication
import com.be.music.data.AppDatabase
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver

actual fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = BeMusicApplication.app()
    return Room.databaseBuilder<AppDatabase>(
        context,
        name = "be_music_database"
    ).setDriver(BundledSQLiteDriver())
}
