package com.forge.bridge.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.forge.bridge.data.local.entities.ProviderEntity

@Database(
    entities = [ProviderEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun providerDao(): ProviderDao

    companion object {
        const val NAME = "forge_bridge.db"
    }
}
