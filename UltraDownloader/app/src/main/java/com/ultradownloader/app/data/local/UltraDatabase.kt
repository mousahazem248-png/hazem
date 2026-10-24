package com.ultradownloader.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadTaskEntity::class],
    version = 1,
    exportSchema = false
)
abstract class UltraDatabase : RoomDatabase() {
    abstract fun downloadTaskDao(): DownloadTaskDao
}
