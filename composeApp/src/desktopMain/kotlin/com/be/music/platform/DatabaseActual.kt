package com.be.music.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.be.music.PlatformDirs
import com.be.music.data.AppDatabase
import java.io.File

fun databaseFile(): File {
    val dir = File(PlatformDirs.appDataDir, "databases")
    dir.mkdirs()
    return File(dir, "be_music_database.db")
}

actual fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder<AppDatabase>(
        name = databaseFile().absolutePath
    ).setDriver(BundledSQLiteDriver())
}
