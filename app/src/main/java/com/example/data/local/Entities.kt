package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_camera_profiles")
data class SavedCameraProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val profileName: String,
    val modelId: String,
    val brand: String,
    val preferredMode: String,
    val wifiIpAddress: String,
    val wifiPort: Int,
    val notes: String,
    val lastConnectedMs: Long
)

@Entity(tableName = "lut_presets")
data class LutPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sourceUri: String,
    val cubeSize: Int,
    val isBuiltIn: Boolean,
    val createdAtMs: Long
)

@Entity(tableName = "recorded_media")
data class RecordedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val filePath: String,
    val mediaType: String, // "VIDEO_MP4" or "SCREENSHOT_PNG"
    val cameraModel: String,
    val resolution: String,
    val durationSeconds: Long,
    val fileSizeBytes: Long,
    val createdAtMs: Long
)
