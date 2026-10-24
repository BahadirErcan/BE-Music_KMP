package com.be.music.platform

import androidx.room.RoomDatabase
import com.be.music.data.AppDatabase

/** Platforma özgü Room database builder (driver + isim dizgisi Hoff). */
expect fun createDatabaseBuilder(): RoomDatabase.Builder<AppDatabase>
