package com.be.music.platform

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.be.music.data.AppDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.URLForDirectory

private fun databaseDir(): String {
    val url: NSURL? = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return (url?.path ?: "") + "/databases"
}

actual fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val fm = NSFileManager.defaultManager
    fm.createDirectoryAtPath(databaseDir(), withIntermediateDirectories = true, attributes = null, error = null)
    return Room.databaseBuilder<AppDatabase>(
        name = databaseDir() + "/be_music_database.db"
    ).setDriver(BundledSQLiteDriver())
}
