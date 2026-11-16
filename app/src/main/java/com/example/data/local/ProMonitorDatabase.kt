package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedCameraProfileEntity::class,
        LutPresetEntity::class,
        RecordedMediaEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ProMonitorDatabase : RoomDatabase() {
    abstract fun cameraProfileDao(): CameraProfileDao
    abstract fun lutPresetDao(): LutPresetDao
    abstract fun recordedMediaDao(): RecordedMediaDao

    companion object {
        @Volatile
        private var INSTANCE: ProMonitorDatabase? = null

        fun getInstance(context: Context): ProMonitorDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProMonitorDatabase::class.java,
                    "promonitor360.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
