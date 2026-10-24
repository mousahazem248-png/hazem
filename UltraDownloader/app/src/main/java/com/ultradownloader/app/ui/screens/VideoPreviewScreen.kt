package com.ultradownloader.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.domain.model.MediaFormat
import com.ultradownloader.app.domain.model.StreamType
import com.ultradownloader.app.ui.components.GradientPrimaryButton
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import com.ultradownloader.app.ui.components.PlatformBadge
import com.ultradownloader.app.ui.theme.AmberWarning

@Composable
fun VideoPreviewScreen(
    analysis: MediaAnalysisResult?,
    selectedFormat: MediaFormat?,
    language: AppLanguage,
    onSelectFormat: (MediaFormat) -> Unit,
    onStartDownload: () -> Unit,
    onBackToHome: () -> Unit
) {
    val context = LocalContext.current
    val isAr = language == AppLanguage.ARABIC

    if (analysis == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isAr) "لم يتم تحديد رابط لتحليله بعد." else "No link analyzed yet.",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onBackToHome) {
                Text(text = if (isAr) "العودة للشاشة الرئيسية" else "Back to Home")
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Metadata Card
        LuxuryGlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlatformBadge(platform = analysis.platform, language = language)
                Text(
                    text = "ID: ${analysis.mediaId}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!analysis.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = analysis.thumbnailUrl,
                    contentDescription = analysis.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = analysis.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isAr) "القناة / الحساب: ${analysis.authorName}" else "Creator / Channel: ${analysis.authorName}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Check whether direct download is authorized or restricted by platform policy
        if (analysis.isDownloadPermitted) {
            LuxuryGlassCard {
                Text(
                    text = if (isAr) "اختر الجودة والصيغة المتاحة فعلياً" else "Select Available Quality & Format",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isAr) {
                        "يعرض التطبيق الجودات الحقيقية المتوفرة من المصدر فقط دون اختلاق دقات وهمية."
                    } else {
                        "Only genuine resolutions provided by the source are listed."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))

                analysis.availableFormats.forEach { format ->
                    val isSelected = selectedFormat?.formatId == format.formatId
                    FormatOptionRow(
                        format = format,
                        isSelected = isSelected,
                        language = language,
                        onClick = { onSelectFormat(format) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
                GradientPrimaryButton(
                    text = if (isAr) "بدء التنزيل الآن" else "Start Download Now",
                    onClick = onStartDownload,
                    enabled = selectedFormat != null,
                    leadingIcon = Icons.Outlined.Download,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            // Transparent, policy-compliant notice when the platform prohibits raw third-party stream extraction
            LuxuryGlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isAr) "التنزيل المباشر مقيد بسياسة المنصة" else "Direct Stream Restricted by Platform Policy",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AmberWarning
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (isAr) {
                        analysis.policyMessageAr ?: "لا تسمح هذه المنصة بتنزيل هذا الفيديو عبر تطبيقات خارجية."
                    } else {
                        analysis.policyMessageEn ?: "This platform restricts third-party raw stream extraction for this link."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                analysis.officialAlternativeUrl?.let { officialUrl ->
                    GradientPrimaryButton(
                        text = if (isAr) {
                            analysis.officialAlternativeLabelAr ?: "استخدام وسيلة التنزيل الرسمية"
                        } else {
                            analysis.officialAlternativeLabelEn ?: "Use Official Platform Option"
                        },
                        leadingIcon = Icons.Outlined.OpenInNew,
                        onClick = {
                            runCatching {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(officialUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun FormatOptionRow(
    format: MediaFormat,
    isSelected: Boolean,
    language: AppLanguage,
    onClick: () -> Unit
) {
    val isAr = language == AppLanguage.ARABIC
    val isAudio = format.streamType == StreamType.AUDIO_ONLY
    val sizeText = format.formattedExpectedSize
        ?: if (isAr) "يُحسب الحجم أثناء التنزيل" else "Size calculated on transfer"

    val streamModeLabel = when (format.streamType) {
        StreamType.VIDEO_MUXED -> if (isAr) "فيديو + صوت مدمج (MP4)" else "Muxed Video + Audio (MP4)"
        StreamType.VIDEO_ADAPTIVE -> if (isAr) "دمج عالي الدقة عبر MediaMuxer" else "Adaptive HD + Audio Mux"
        StreamType.AUDIO_ONLY -> if (isAr) "ملف صوتي فقط (${format.extension.uppercase()})" else "Audio Only (${format.extension.uppercase()})"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = if (isSelected) 1.8.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                RadioButton(selected = isSelected, onClick = onClick)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isAudio) Icons.Outlined.AudioFile else Icons.Outlined.VideoFile,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = format.qualityLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$streamModeLabel • $sizeText",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
