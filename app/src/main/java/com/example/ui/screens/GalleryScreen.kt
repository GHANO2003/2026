package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.localization.AppLanguage
import com.example.core.localization.LocalAppStrings
import com.example.core.models.RecordingStatus
import com.example.data.local.RecordedMediaEntity
import com.example.ui.components.PreviewOnlyBanner
import com.example.ui.components.StatusPill
import com.example.ui.theme.CharcoalBorder
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CharcoalPanel
import com.example.ui.theme.CinemaBlack
import com.example.ui.theme.RecordingRed
import com.example.ui.theme.SignalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GalleryScreen(
    mediaList: List<RecordedMediaEntity>,
    recordingStatus: RecordingStatus,
    language: AppLanguage,
    onShareMedia: (RecordedMediaEntity) -> Unit,
    onDeleteMedia: (RecordedMediaEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val isArabic = language == AppLanguage.ARABIC

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CinemaBlack),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isArabic) "معرض التسجيلات واللقطات" else "Recording & Screenshot Gallery",
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                StatusPill(
                    label = "${recordingStatus.availableStorageMb} MB FREE",
                    isActive = recordingStatus.availableStorageMb > 250
                )
            }
        }

        item {
            PreviewOnlyBanner(text = strings.hdmiRawDisclaimer)
        }

        if (mediaList.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CharcoalPanel)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(16.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, tint = TextMuted, modifier = Modifier.size(44.dp))
                    Text(
                        text = if (isArabic) "لا توجد تسجيلات أو لقطات محفوظة بعد" else "No MP4 recordings or frame grabs yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = if (isArabic)
                            "استخدم شاشة المراقب لالتقاط لقطة شاشة أو تسجيل إشارة HDMI بصيغة MP4."
                        else
                            "Use the Live Monitor toolbar to save frame grabs or record incoming HDMI video to MP4.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(mediaList, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(CharcoalPanel)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val thumb = remember(item.filePath) {
                        if (item.mediaType == "SCREENSHOT_PNG") {
                            runCatching {
                                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeFile(item.filePath, opts)
                            }.getOrNull()
                        } else null
                    }
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CharcoalElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        if (thumb != null) {
                            Image(
                                bitmap = thumb.asImageBitmap(),
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                if (item.mediaType == "VIDEO_MP4") Icons.Default.Videocam else Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = if (item.mediaType == "VIDEO_MP4") RecordingRed else SignalCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            "${item.cameraModel} • ${item.resolution} • ${item.fileSizeBytes / 1024} KB",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        if (item.mediaType == "VIDEO_MP4") {
                            Text("MP4 Duration: ${item.durationSeconds}s", style = MaterialTheme.typography.labelSmall, color = SignalCyan)
                        }
                    }

                    IconButton(
                        onClick = { onShareMedia(item) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("gallery_share_${item.id}")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share media", tint = SignalCyan)
                    }
                    IconButton(
                        onClick = { onDeleteMedia(item) },
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("gallery_delete_${item.id}")
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete media", tint = TextSecondary)
                    }
                }
            }
        }
    }
}
