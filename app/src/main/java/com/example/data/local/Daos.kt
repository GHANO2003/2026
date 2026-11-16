package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraProfileDao {
    @Query("SELECT * FROM saved_camera_profiles ORDER BY lastConnectedMs DESC")
    fun observeProfiles(): Flow<List<SavedCameraProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: SavedCameraProfileEntity): Long

    @Query("DELETE FROM saved_camera_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)
}

@Dao
interface LutPresetDao {
    @Query("SELECT * FROM lut_presets ORDER BY isBuiltIn DESC, createdAtMs DESC")
    fun observeLuts(): Flow<List<LutPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLut(lut: LutPresetEntity)

    @Query("DELETE FROM lut_presets WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomLut(id: String)
}

@Dao
interface RecordedMediaDao {
    @Query("SELECT * FROM recorded_media ORDER BY createdAtMs DESC")
    fun observeMedia(): Flow<List<RecordedMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: RecordedMediaEntity): Long

    @Query("DELETE FROM recorded_media WHERE id = :id")
    suspend fun deleteMediaById(id: Long)
}
