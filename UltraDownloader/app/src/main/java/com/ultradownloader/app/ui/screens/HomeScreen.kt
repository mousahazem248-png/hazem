package com.ultradownloader.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ultradownloader.app.data.local.AppLanguage
import com.ultradownloader.app.domain.model.PlatformType
import com.ultradownloader.app.ui.components.GradientPrimaryButton
import com.ultradownloader.app.ui.components.LuxuryGlassCard
import com.ultradownloader.app.ui.components.PlatformBadge
import com.ultradownloader.app.ui.theme.UltraBrandGradient
import com.ultradownloader.app.ui.viewmodel.HomeUiState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    language: AppLanguage,
    onUrlChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onOpenPreviewClick: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val isAr = language == AppLanguage.ARABIC
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Brand Header with Electric Purple-to-Blue Icon & Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(UltraBrandGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = "Ultra Downloader Logo",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Ultra Downloader",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (isAr) "منزّل الفيديوهات الاحترافي والآمن" else "Luxury & Compliant Media Downloader",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Link Input Card
            LuxuryGlassCard {
                Text(
                    text = if (isAr) "الصق رابط الفيديو أو الصوت هنا" else "Paste video or audio link here",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.urlInput,
                    onValueChange = onUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "https://www.youtube.com/watch?v=... | TikTok | Instagram",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.urlInput.isNotEmpty()) {
                            IconButton(onClick = { onUrlChange("") }) {
                                Icon(
                                    imageVector = Icons.Outlined.Clear,
                                    contentDescription = if (isAr) "مسح" else "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                // Automatic Platform Detection Badge
                if (uiState.urlInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isAr) "المنصة المكتشفة تلقائياً:" else "Auto-Detected Platform:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        PlatformBadge(
                            platform = uiState.detectedPlatform,
                            language = language
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Explicit Privacy-Respecting Clipboard Paste Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            // Reads clipboard ONLY when the user explicitly taps this button
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip
                                ?.takeIf { it.itemCount > 0 }
                                ?.getItemAt(0)
                                ?.coerceToText(context)
                                ?.toString()
                                ?.trim()

                            if (!clipText.isNullOrBlank()) {
                                onUrlChange(clipText)
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        if (isAr) "الحافظة لا تحتوي على رابط نصي" else "Clipboard does not contain a text link"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = if (isAr) "لصق من الحافظة" else "Paste Link")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Main CTA Button: «تحليل الرابط» / "Analyze Link"
                GradientPrimaryButton(
                    text = if (isAr) "تحليل الرابط" else "Analyze Link",
                    onClick = onAnalyzeClick,
                    enabled = uiState.urlInput.isNotBlank(),
                    isLoading = uiState.isAnalyzing,
                    leadingIcon = Icons.Outlined.Search,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAr) {
                            "لا يتم قراءة الحافظة إلا عند ضغطك على زر اللصق • اتصال HTTPS مشفر"
                        } else {
                            "Clipboard read only on tap • Encrypted HTTPS & SSRF verified"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Preview Card on Home Screen when a link was recently analyzed
            uiState.latestAnalysis?.let { analysis ->
                LuxuryGlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isAr) "آخر معاينة تم جلبها" else "Latest Analyzed Media",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        PlatformBadge(platform = analysis.platform, language = language)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (!analysis.thumbnailUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = analysis.thumbnailUrl,
                            contentDescription = analysis.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(175.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Text(
                        text = analysis.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAr) "القناة / الحساب: ${analysis.authorName}" else "Creator: ${analysis.authorName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = onOpenPreviewClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (isAr) "عرض خيارات الجودة والتنزيل" else "Open Quality & Download Options"
                        )
                    }
                }
            }

            // Supported Platforms Overview Card
            LuxuryGlassCard {
                Text(
                    text = if (isAr) "المنصات المدعومة وآلية التكامل القانونية" else "Supported Platforms & Compliant Integration",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                val items = if (isAr) {
                    listOf(
                        "YouTube: جلب بيانات الفيديو والصورة المصغرة عبر oEmbed الرسمي، وتنزيل المحتوى المملوك أو المصرح به قانونياً.",
                        "TikTok: دعم الروابط العامة المتاحة للتنزيل وفق شروط المنصة وصلاحيات صانع المحتوى.",
                        "Instagram: دعم Reels والفيديوهات العامة المصرح بها دون طلب تسجيل دخول أو تجاوز الحسابات الخاصة.",
                        "Direct Media: تنزيل روابط MP4 وMP3 المباشرة مع دعم الإيقاف المؤقت والاستئناف والتحقق من SHA-256."
                    )
                } else {
                    listOf(
                        "YouTube: Official oEmbed metadata preview and legal download of user-owned/authorized streams.",
                        "TikTok: Public video links with creator download permissions respected.",
                        "Instagram: Authorized public Reels & Videos without login scraping or private account bypass.",
                        "Direct Media: Direct HTTPS MP4/MP3 streams with HTTP Range pause/resume & SHA-256 check."
                    )
                }
                items.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
