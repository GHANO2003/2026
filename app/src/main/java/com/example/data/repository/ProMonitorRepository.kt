package com.example.data.repository

import com.example.data.local.CameraProfileDao
import com.example.data.local.LutPresetDao
import com.example.data.local.LutPresetEntity
import com.example.data.local.RecordedMediaDao
import com.example.data.local.RecordedMediaEntity
import com.example.data.local.SavedCameraProfileEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

class ProMonitorRepository(
    private val profileDao: CameraProfileDao,
    private val lutDao: LutPresetDao,
    private val mediaDao: RecordedMediaDao
) {
    val savedProfiles: Flow<List<SavedCameraProfileEntity>> = profileDao.observeProfiles()
    val luts: Flow<List<LutPresetEntity>> = lutDao.observeLuts()
    val recordedMedia: Flow<List<RecordedMediaEntity>> = mediaDao.observeMedia()

    suspend fun ensureBuiltInLuts() {
        val now = System.currentTimeMillis()
        val builtIns = listOf(
            LutPresetEntity("builtin_rec709", "Rec.709 Neutral", "builtin://rec709", 17, true, now),
            LutPresetEntity("builtin_cinema_teal", "Cinema Teal & Orange", "builtin://cinema_teal", 17, true, now),
            LutPresetEntity("builtin_warm_film", "Kodak Warm Film 2383", "builtin://warm_film", 17, true, now),
            LutPresetEntity("builtin_noir_bw", "High Contrast Noir B&W", "builtin://noir_bw", 17, true, now),
            LutPresetEntity("builtin_nikon_flat", "Nikon Flat to Rec.709", "builtin://nikon_flat", 17, true, now),
            LutPresetEntity("builtin_canon_clog", "Canon Cinema Neutral", "builtin://canon_neutral", 17, true, now)
        )
        builtIns.forEach { lutDao.upsertLut(it) }
    }

    suspend fun saveProfile(profile: SavedCameraProfileEntity): Long {
        return profileDao.upsertProfile(profile)
    }

    suspend fun deleteProfile(id: Long) {
        profileDao.deleteProfile(id)
    }

    suspend fun saveCustomLut(lut: LutPresetEntity) {
        lutDao.upsertLut(lut)
    }

    suspend fun deleteCustomLut(id: String) {
        lutDao.deleteCustomLut(id)
    }

    suspend fun recordMediaEntry(entry: RecordedMediaEntity): Long {
        return mediaDao.insertMedia(entry)
    }

    suspend fun deleteMedia(media: RecordedMediaEntity) {
        runCatching { File(media.filePath).delete() }
        mediaDao.deleteMediaById(media.id)
    }
}
