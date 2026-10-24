package com.ultradownloader.app.ui.screens

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
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.domain.model.DownloadStatus
import com.ultradownloader.app.domain.model.DownloadTask
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import com.ultradownloader.app.ui.components.PlatformBadge
import com.ultradownloader.app.ui.components.TransferFormatters
import com.ultradownloader.app.ui.theme.CrimsonError

@Composable
fun ActiveDownloadsScreen(
    tasks: List<DownloadTask>,
    language: AppLanguage,
    onPause: (String) -> Unit,
    onResumeOrRetry: (String) -> Unit,
    onCancelDelete: (String) -> Unit
) {
    val isAr = language == AppLanguage.ARABIC

    if (tasks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Outlined.CloudQueue,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isAr) "لا توجد تنزيلات جارية حالياً" else "No active downloads right now",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isAr) {
                        "الصق رابط فيديو في الشاشة الرئيسية لبدء التنزيل السريع في الخلفية."
                    } else {
                        "Paste a video link on the Home screen to start a background transfer."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = if (isAr) "التنزيلات الجارية (${tasks.size})" else "Active Transfers (${tasks.size})",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(tasks, key = { it.taskId }) { task ->
            ActiveDownloadItemCard(
                task = task,
                language = language,
                onPause = { onPause(task.taskId) },
                onResumeOrRetry = { onResumeOrRetry(task.taskId) },
                onCancelDelete = { onCancelDelete(task.taskId) }
            )
        }
    }
}

@Composable
private fun ActiveDownloadItemCard(
    task: DownloadTask,
    language: AppLanguage,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onCancelDelete: () -> Unit
) {
    val isAr = language == AppLanguage.ARABIC

    val statusText = when (task.status) {
        DownloadStatus.QUEUED -> if (isAr) "في قائمة الانتظار…" else "Queued…"
        DownloadStatus.DOWNLOADING -> if (isAr) "جاري التنزيل (${task.progressPercent}%)" else "Downloading (${task.progressPercent}%)"
        DownloadStatus.MERGING_STREAMS -> if (isAr) "جاري دمج الفيديو والصوت عبر MediaMuxer…" else "Merging Video & Audio via MediaMuxer…"
        DownloadStatus.VERIFYING_INTEGRITY -> if (isAr) "جاري التحقق من بصمة SHA-256 وسلامة الملف…" else "Verifying SHA-256 & container integrity…"
        DownloadStatus.PAUSED -> if (isAr) "متوقف مؤقتاً" else "Paused"
        DownloadStatus.FAILED -> if (isAr) (task.errorMessageAr ?: "فشل التنزيل") else (task.errorMessageEn ?: "Download failed")
        else -> task.status.name
    }

    LuxuryGlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlatformBadge(platform = task.platform, language = language)
            Text(
                text = "${task.qualityLabel} • ${task.extension.uppercase()}",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = if (task.status == DownloadStatus.FAILED) CrimsonError else MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { task.progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Real-time Transferred Bytes, Speed, and ETA
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val transferredStr = if (task.totalBytes > 0) {
                "${TransferFormatters.formatBytes(task.downloadedBytes)} / ${TransferFormatters.formatBytes(task.totalBytes)}"
            } else {
                TransferFormatters.formatBytes(task.downloadedBytes)
            }
            Text(
                text = transferredStr,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (task.status == DownloadStatus.DOWNLOADING) {
                Text(
                    text = "${TransferFormatters.formatSpeed(task.speedBytesPerSec)} • ${TransferFormatters.formatEta(task.etaSeconds, language)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (task.status) {
                DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                    if (task.supportsPauseResume) {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Outlined.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = if (isAr) "إيقاف مؤقت" else "Pause")
                        }
                    }
                }
                DownloadStatus.PAUSED -> {
                    OutlinedButton(
                        onClick = onResumeOrRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isAr) "استئناف" else "Resume")
                    }
                }
                DownloadStatus.FAILED -> {
                    OutlinedButton(
                        onClick = onResumeOrRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = if (isAr) "إعادة المحاولة" else "Retry")
                    }
                }
                else -> {}
            }

            OutlinedButton(
                onClick = onCancelDelete,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (isAr) "إلغاء وحذف" else "Cancel")
            }
        }
    }
}
